/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.junit.Assert;

/**
 * Internal class used by test framework.Boots up the model controller used for the test.
 * While the super class {@link AbstractControllerService} exists here in the main code source, for the legacy controllers it is got from the
 * xxxx/test-controller-xxx jars instead (see the constructor javadocs for more information)
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
//TODO find better way to support legacy ModelTestModelControllerService without need for having all old methods still present on AbstractControllerService
public abstract class ModelTestModelControllerService extends AbstractControllerService {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final StringConfigurationPersister persister;
    private final TransformerRegistry transformerRegistry;
    private final ModelTestOperationValidatorFilter validateOpsFilter;
    private final RunningModeControl runningModeControl;
    private volatile ManagementResourceRegistration rootRegistration;
    private volatile Throwable error;
    private volatile boolean bootSuccess;

    /**
     * This is the constructor to use for the legacy controller using core-model-test/test-controller-7.1.x and subsystem-test/test-controller-7.1.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
                           final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
                           final DescriptionProvider rootDescriptionProvider, ControlledProcessState processState, Controller71x version) {
        // Fails in core-model-test transformation testing if ExpressionResolver.TEST_RESOLVER is used because not present in 7.1.x
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootDescriptionProvider, null, getExpressionResolverFor71());
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for core-model/test-controller-7.2.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState, Controller72x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    private static ExpressionResolver getExpressionResolverFor71() {
        try {
            Field defaultExpressionResolver = ExpressionResolver.class.getDeclaredField("DEFAULT");
            return (ExpressionResolver) defaultExpressionResolver.get(null);
        } catch (NoSuchFieldException ex) {
        } catch (IllegalAccessException ex) {}
        return ExpressionResolver.TEST_RESOLVER;
    }

    /**
     * This is the constructor to use for subsystem-test/test-controller-7.2.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DescriptionProvider rootDescriptionProvider, ControlledProcessState processState, Controller72x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootDescriptionProvider, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }


    /**
     * This is the constructor to use for core-model/test-controller-7.3.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState, Controller73x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for subsystem-test/test-controller-7.3.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DescriptionProvider rootDescriptionProvider, ControlledProcessState processState, Controller73x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootDescriptionProvider, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for core-model/test-controller-7.4.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState, Controller74x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootResourceDefinition, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for subsystem-test/test-controller-7.4.x
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DescriptionProvider rootDescriptionProvider, ControlledProcessState processState, Controller74x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootDescriptionProvider, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for 8.0.x core model tests
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState,
            ExpressionResolver expressionResolver, Controller80x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootResourceDefinition, null,
                expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer());
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for 8.0.x subsystem tests
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final ResourceDefinition resourceDefinition, ControlledProcessState processState, Controller80x version) {
        // Fails in core-model-test transformation testing if ExpressionResolver.TEST_RESOLVER is used because not present in 7.1.x
        super(processType, runningModeControl, persister,
         processState == null ? new ControlledProcessState(true) : processState, resourceDefinition, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for 9.0.x core model tests
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final DelegatingResourceDefinition rootResourceDefinition, ControlledProcessState processState,
            ExpressionResolver expressionResolver, Controller90x version) {
        super(processType, runningModeControl, persister,
                processState == null ? new ControlledProcessState(true) : processState, rootResourceDefinition, null,
                expressionResolver, AuditLogger.NO_OP_LOGGER, new DelegatingConfigurableAuthorizer());
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }

    /**
     * This is the constructor to use for 9.0.x subsystem tests
     */
    protected ModelTestModelControllerService(final ProcessType processType, final RunningModeControl runningModeControl, final TransformerRegistry transformerRegistry,
            final StringConfigurationPersister persister, final ModelTestOperationValidatorFilter validateOpsFilter,
            final ResourceDefinition resourceDefinition, ControlledProcessState processState, Controller90x version) {
        super(processType, runningModeControl, persister,
         processState == null ? new ControlledProcessState(true) : processState, resourceDefinition, null, ExpressionResolver.TEST_RESOLVER);
        this.persister = persister;
        this.transformerRegistry = transformerRegistry;
        this.validateOpsFilter = validateOpsFilter;
        this.runningModeControl = runningModeControl;
    }
    public boolean isSuccessfulBoot() {
        return bootSuccess;
    }

    public Throwable getBootError() {
        return error;
    }

    RunningMode getRunningMode() {
        return runningModeControl.getRunningMode();
    }

    ProcessType getProcessType() {
        return processType;
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        this.rootRegistration = managementModel.getRootResourceRegistration();
        initCoreModel(managementModel, modelControllerResource);
        initExtraModel(managementModel);
    }

    /** @deprecated only for legacy version support */
    @Deprecated
    protected void initModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource) {
        this.rootRegistration = rootRegistration;
        initCoreModel(rootResource, rootRegistration, modelControllerResource);
        initExtraModel(rootResource, rootRegistration);
    }

    @SuppressWarnings("deprecation")
    protected void initCoreModel(ManagementModel managementModel, Resource modelControllerResource) {
        initCoreModel(managementModel.getRootResource(), managementModel.getRootResourceRegistration(), modelControllerResource);
    }

    /** @deprecated only for legacy version support */
    @Deprecated
    protected void initCoreModel(Resource rootResource, ManagementResourceRegistration rootRegistration, Resource modelControllerResource) {
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, ProcessType.STANDALONE_SERVER);

        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
    }

    @SuppressWarnings("deprecation")
    protected void initExtraModel(ManagementModel managementModel) {
        initExtraModel(managementModel.getRootResource(), managementModel.getRootResourceRegistration());
    }

    /** @deprecated only for legacy version support */
    @Deprecated
    protected void initExtraModel(Resource rootResource, ManagementResourceRegistration rootRegistration) {
    }

    TransformerRegistry getTransformersRegistry() {
        return transformerRegistry;
    }

    @Override
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) throws ConfigurationPersistenceException {
        try {
            preBoot(bootOperations, rollbackOnRuntimeFailure);
            OperationValidator validator = new OperationValidator(rootRegistration);
            for (ModelNode op : bootOperations) {
                ModelNode toValidate = validateOpsFilter.adjustForValidation(op.clone());
                if (toValidate != null) {
                    validator.validateOperation(toValidate);
                }
            }
            bootSuccess = super.boot(persister.getBootOperations(), rollbackOnRuntimeFailure);
            return bootSuccess;
        } catch (Exception e) {
            error = e;
        } catch (Throwable t) {
            error = new Exception(t);
        } finally {
            postBoot();
        }
        return false;
    }

    protected void preBoot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) {
    }

    protected void postBoot() {
    }

    @Override
    protected void bootThreadDone() {
        try {
            super.bootThreadDone();
        } finally {
            countdownDoneLatch();
        }
    }

    protected void countdownDoneLatch() {
        latch.countDown();
    }

    @Override
    public void start(StartContext context) throws StartException {
        try {
            super.start(context);
        } catch (StartException e) {
            error = e;
            e.printStackTrace();
            latch.countDown();
            throw e;
        } catch (Throwable t) {
            error = t;
            latch.countDown();
            throw new StartException(t);
        }
    }

    public void waitForSetup() throws Exception {
        latch.await();
        if (error != null) {
            if (error instanceof Exception)
                throw (Exception) error;
            throw new RuntimeException(error);
        }
    }

    public ManagementResourceRegistration getRootRegistration() {
        return rootRegistration;
    }

    /**
     * Grabs the current root resource. This cannot be called after the kernelServices
     * have been shut down
     *
     * @param kernelServices the kernel services used to access the controller
     */
    public static Resource grabRootResource(ModelTestKernelServices<?> kernelServices) {
        final AtomicReference<Resource> resourceRef = new AtomicReference<Resource>();
        ModelNode fakeOP = new ModelNode();
        fakeOP.get(OP).set("fake");
        ((ModelTestKernelServicesImpl<?>)kernelServices).internalExecute(fakeOP, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                resourceRef.set(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
                context.getResult().setEmptyObject();
            }
        });
        Resource rootResource = resourceRef.get();
        Assert.assertNotNull(rootResource);
        return rootResource;
    }

    @Override
    protected ModelNode internalExecute(final ModelNode operation, final OperationMessageHandler handler,
                                        final ModelController.OperationTransactionControl control,
                                        final OperationAttachments attachments, final OperationStepHandler prepareStep) {
        return super.internalExecute(operation, handler, control, attachments, prepareStep);
    }

    public static final DescriptionProvider DESC_PROVIDER = new DescriptionProvider() {
        @Override
        public ModelNode getModelDescription(Locale locale) {
            ModelNode model = new ModelNode();
            model.get(DESCRIPTION).set("The test model controller");
            return model;
        }
    };

    public static class DelegatingResourceDefinition implements ResourceDefinition {
        private volatile ResourceDefinition delegate;

        public void setDelegate(ResourceDefinition delegate) {
            this.delegate = delegate;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            delegate.registerOperations(resourceRegistration);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            delegate.registerChildren(resourceRegistration);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            delegate.registerAttributes(resourceRegistration);
        }

        @Override
        public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            delegate.registerNotifications(resourceRegistration);
        }

        @Override
        public PathElement getPathElement() {
            return delegate.getPathElement();
        }

        @Override
        public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration resourceRegistration) {
            return delegate.getDescriptionProvider(resourceRegistration);
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            if (delegate == null) {
                return Collections.emptyList();
            }
            return delegate.getAccessConstraints();
        }

        @Override
        public boolean isRuntime() {
            return delegate.isRuntime();
        }

        @Override
        public boolean isOrderedChild() {
            if (delegate == null) {
                return false;
            }
            return delegate.isOrderedChild();
        }
    }

    //These are here to overload the constuctor used for the different legacy controllers

    public static class Controller71x {
        public static Controller71x INSTANCE = new Controller71x();
        private Controller71x() {
        }
    }

    public static class Controller72x {
        public static Controller72x INSTANCE = new Controller72x();
        private Controller72x() {
        }
    }

    public static class Controller73x {
        public static Controller73x INSTANCE = new Controller73x();
        private Controller73x() {
        }
    }

    public static class Controller74x {
        public static Controller74x INSTANCE = new Controller74x();
        private Controller74x() {
        }
    }

    public static class Controller80x {
        public static Controller80x INSTANCE = new Controller80x();
        private Controller80x() {
        }
    }

    public static class Controller90x {
        public static Controller90x INSTANCE = new Controller90x();
        private Controller90x() {
        }
    }

}
