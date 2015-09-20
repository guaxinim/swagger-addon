package com.tdc.swagger.addon;

import com.tdc.addon.swagger.facet.SwaggerFacet;
import com.tdc.addon.swagger.facet.SwaggerFacetImpl;
import com.tdc.addon.swagger.ui.SwaggerSetupCommand;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.maven.plugins.MavenPluginAdapter;
import org.jboss.forge.addon.maven.projects.MavenPluginFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.shell.test.ShellTest;
import org.jboss.forge.addon.ui.command.AbstractCommandExecutionListener;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.controller.CommandController;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.result.Failed;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.test.UITestHarness;
import org.jboss.forge.arquillian.AddonDependencies;
import org.jboss.forge.arquillian.archive.AddonArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class SwaggerSetupCommandTest {

    @Deployment
    @AddonDependencies
    public static AddonArchive getDeployment() {
        return ShrinkWrap.create(AddonArchive.class).addBeansXML().addPackages(true, "com.tdc.addon.swagger");
    }

    @Inject
    private ProjectFactory projectFactory;

    @Inject
    private FacetFactory facetFactory;

    @Inject
    private UITestHarness uiTestHarness;

    @Inject
    private ShellTest shellTest;

    private Project project;

    @Before
    public void setUp() {
        project = projectFactory.createTempProject();
        facetFactory.install(project, SwaggerFacet.class);
    }
    
    @Test
    public void checkCommandMetadata() throws Exception {
        try (CommandController controller = uiTestHarness.createCommandController(SwaggerSetupCommand.class,
                project.getRoot())) {
            controller.initialize();
            // Checks the command metadata
            assertTrue(controller.getCommand() instanceof SwaggerSetupCommand);
            UICommandMetadata metadata = controller.getMetadata();
            assertEquals("Swagger: Setup", metadata.getName());
            assertEquals("Swagger", metadata.getCategory().getName());
            assertNull(metadata.getCategory().getSubCategory());
            assertEquals(2, controller.getInputs().size());
            assertFalse(controller.hasInput("fake input"));
            assertTrue(controller.hasInput("docBaseDir"));
            assertTrue(controller.hasInput("apiBasePath"));
        }
    }

    @Test
    public void checkCommandShell() throws Exception {
        shellTest.getShell().setCurrentResource(project.getRoot());
        Result result = shellTest.execute(("Swagger: Setup"), 10, TimeUnit.SECONDS);

        Assert.assertThat(result, not(instanceOf(Failed.class)));
        Assert.assertTrue(project.hasFacet(SwaggerFacet.class));
    }

    @Test
    public void testSwaggerSetup() throws Exception {
        facetFactory.install(project, SwaggerFacet.class);
        try (CommandController controller = uiTestHarness.createCommandController(SwaggerSetupCommand.class,
                project.getRoot())) {
            controller.initialize();
            Assert.assertTrue(controller.isValid());
            final AtomicBoolean flag = new AtomicBoolean();
            controller.getContext().addCommandExecutionListener(new AbstractCommandExecutionListener() {
                @Override
                public void postCommandExecuted(UICommand command, UIExecutionContext context, Result result) {
                    if (result.getMessage().equals("Swagger setup completed successfully!")) {
                        flag.set(true);
                    }
                }
            });
            controller.execute();
            Assert.assertTrue(flag.get());
            SwaggerFacet facet = project.getFacet(SwaggerFacet.class);
            Assert.assertTrue(facet.isInstalled());

            MavenPluginAdapter swaggerPlugin = (MavenPluginAdapter) project.getFacet(MavenPluginFacet.class)
                    .getEffectivePlugin(SwaggerFacetImpl.JAVADOC_PLUGIN_COORDINATE);
            Assert.assertEquals("maven-javadoc-plugin", swaggerPlugin.getCoordinate().getArtifactId());
            Assert.assertEquals(1, swaggerPlugin.getExecutions().size());
            PluginExecution exec = swaggerPlugin.getExecutions().get(0);
            assertEquals(exec.getId(), SwaggerFacetImpl.SWAGGER_DOCLET_EXECUTION_ID);
        }
    }

    @Test
    public void testSwaggerSetupWithParameters() throws Exception {
        facetFactory.install(project, SwaggerFacet.class);
        try (CommandController controller = uiTestHarness.createCommandController(SwaggerSetupCommand.class,
                project.getRoot())) {
            controller.initialize();
            controller.setValueFor("docBaseDir", "/apidocs");
            controller.setValueFor("apiBasePath", "/rest");
            Assert.assertTrue(controller.isValid());

            final AtomicBoolean flag = new AtomicBoolean();
            controller.getContext().addCommandExecutionListener(new AbstractCommandExecutionListener() {
                @Override
                public void postCommandExecuted(UICommand command, UIExecutionContext context, Result result) {
                    if (result.getMessage().equals("Swagger setup completed successfully!")) {
                        flag.set(true);
                    }
                }
            });
            controller.execute();
            Assert.assertTrue(flag.get());
            SwaggerFacet facet = project.getFacet(SwaggerFacet.class);
            Assert.assertTrue(facet.isInstalled());

            MavenPluginAdapter swaggerPlugin = (MavenPluginAdapter) project.getFacet(MavenPluginFacet.class)
                    .getEffectivePlugin(SwaggerFacetImpl.JAVADOC_PLUGIN_COORDINATE);
            Assert.assertEquals("maven-javadoc-plugin", swaggerPlugin.getCoordinate().getArtifactId());
            Assert.assertEquals(1, swaggerPlugin.getExecutions().size());
            PluginExecution exec = swaggerPlugin.getExecutions().get(0);
            assertEquals(exec.getId(), SwaggerFacetImpl.SWAGGER_DOCLET_EXECUTION_ID);
            Xpp3Dom execConfig = (Xpp3Dom) exec.getConfiguration();
            assertEquals(execConfig.getChildCount(), 5);
            assertEquals(execConfig.getChild("doclet").getValue(), "com.carma.swagger.doclet.ServiceDoclet");
            String projectName = project.getFacet(MetadataFacet.class).getProjectName();
            assertEquals(execConfig.getChild("additionalparam").getValue(),"-apiVersion 1\n" +
"		-docBasePath "
                    + projectName
                    + "/apidocs\n" +
"		-apiBasePath "
                    +  projectName
                    + "/rest\n" +
"		-swaggerUiPath ${project.build.directory}/");
        }
    }

    @Test
    public void testSwaggerSetupWithNullParameters() throws Exception {
        facetFactory.install(project, SwaggerFacet.class);
        try (CommandController controller = uiTestHarness.createCommandController(SwaggerSetupCommand.class,
                project.getRoot())) {
            controller.initialize();
            controller.setValueFor("docBaseDir", null);
            controller.setValueFor("apiBasePath", null);
            Assert.assertTrue(controller.isValid());

            final AtomicBoolean flag = new AtomicBoolean();
            controller.getContext().addCommandExecutionListener(new AbstractCommandExecutionListener() {
                @Override
                public void postCommandExecuted(UICommand command, UIExecutionContext context, Result result) {
                    if (result.getMessage().equals("Swagger setup completed successfully!")) {
                        flag.set(true);
                    }
                }
            });
            controller.execute();
            Assert.assertTrue(flag.get());
            SwaggerFacet facet = project.getFacet(SwaggerFacet.class);
            Assert.assertTrue(facet.isInstalled());

            MavenPluginAdapter swaggerPlugin = (MavenPluginAdapter) project.getFacet(MavenPluginFacet.class)
                    .getEffectivePlugin(SwaggerFacetImpl.JAVADOC_PLUGIN_COORDINATE);
            Assert.assertEquals("maven-javadoc-plugin", swaggerPlugin.getCoordinate().getArtifactId());
            Assert.assertEquals(1, swaggerPlugin.getExecutions().size());
            Assert.assertEquals(SwaggerFacetImpl.SWAGGER_DOCLET_EXECUTION_ID, swaggerPlugin.getExecutions().get(0).getId());
            
            String projectName = project.getFacet(MetadataFacet.class).getProjectName();
            Assert.assertEquals(((Xpp3Dom) swaggerPlugin.getExecutionsAsMap().get(SwaggerFacetImpl.SWAGGER_DOCLET_EXECUTION_ID).getConfiguration()).getChild("additionalparam").getValue(),"-apiVersion 1\n" +
"		-docBasePath "
                    + "" +projectName
                    + "/apidocs\n" +
"		-apiBasePath "
                    + "" +projectName
                    + "/rest\n" +
"		-swaggerUiPath ${project.build.directory}/");
        }
    }

}