package com.checkmarx.intellij.unit.devassist.scanners.containers;

import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerCommand;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ContainerScannerCommandTest {

    private Disposable disposable;
    private Project project;

    @BeforeEach
    void setUp() {
        disposable = mock(Disposable.class);
        project = mock(Project.class);
    }

    @Test
    void configMatchesContainerScannerConfig() {
        ContainerScannerCommand command = new ContainerScannerCommand(disposable, project);

        ScannerConfig expected = ContainerScannerService.createConfig();
        assertNotNull(command.config);
        assertEquals(expected, command.config);
    }

    @Test
    void privateConstructorStoresProvidedServiceInstance() throws Exception {
        ContainerScannerService containerServiceMock = mock(ContainerScannerService.class);
        ContainerScannerCommand command = createCommandWith(containerServiceMock);

        ContainerScannerService storedService = (ContainerScannerService) readField(command, "containerScannerService");
        assertSame(containerServiceMock, storedService);
    }

    @Test
    void privateConstructorStoresProvidedProjectReference() throws Exception {
        ContainerScannerService customService = mock(ContainerScannerService.class);
        ContainerScannerCommand command = createCommandWith(customService);

        Project storedProject = (Project) readField(command, "project");
        assertSame(project, storedProject);
    }

    private ContainerScannerCommand createCommandWith(ContainerScannerService service) throws Exception {
        Constructor<ContainerScannerCommand> constructor =
                ContainerScannerCommand.class.getDeclaredConstructor(Disposable.class, Project.class, ContainerScannerService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(disposable, project, service);
    }

    private Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

