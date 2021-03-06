package org.bytegeeks.learn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

@Component
public class GroovyMain implements ApplicationContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyMain.class);

    private static ApplicationContext appContext = null;

    private static Map<String, Long> mapFileModicationTimes = new HashMap<String, Long>();

    private static GroovyShell shell;

    private static Map<Object, Object> mapBindings = new HashMap<Object, Object>();

    private static Path scriptsDir = Paths.get(System.getProperty("user.dir"));

    private static final String BEAN_ALIASING_FILE = "beanAliases.properties";
    private static WatchService watcher;

    @PostConstruct
    public void postInit() throws IOException {
        new Thread(new Runnable() {

            public void run() {
                setGroovyEnvironment();
                loadBeanAliases();
                try {
                    startGroovyWatcher();
                } catch (IOException e) {
                    LOG.error("Error in startGroovyWatcher", e);
                }
            }
        }, "Groovy Scripts watcher").start();
    }

    private void loadBeanAliases() {
        try {
            LOG.info("Creating bean aliases from '{}'", BEAN_ALIASING_FILE);
            Properties aliases = new Properties();
            FileInputStream is = new FileInputStream(BEAN_ALIASING_FILE);
            aliases.load(is);
            is.close();
            for (String name : aliases.stringPropertyNames()) {
                String beanRef = aliases.getProperty(name);
                try {
                    Object bean = appContext.getBean(beanRef);
                    shell.setVariable(name, bean);
                    LOG.info("Creating '{}' as alias for bean '{}'", name, beanRef);
                } catch (Exception e) {
                    LOG.error("Can't create alias name '{}', as bean '{}' doesn't exist. Exception: {}", name, beanRef,
                            e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Unable to read bean aliases. Reason: {}", e.getMessage());
        }
    }

    public void startGroovyWatcher() throws IOException {
        LOG.info("Monitoring '{}' for any changes in *.groovy files", scriptsDir.toAbsolutePath());
        watcher = scriptsDir.getFileSystem().newWatchService();
        scriptsDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey watckKey = null;
        while (true) {
            try {
                watckKey = watcher.take();
                Thread.sleep(1000l);
                List<WatchEvent<?>> events = watckKey.pollEvents();
                for (WatchEvent event : events) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE || event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        String changedFile = event.context().toString();
                        if (changedFile.endsWith(".groovy")) {
                            File codeFile = new File(changedFile);
                            long lastModifiedTime = codeFile.lastModified();
                            long lastModifiedTimeKnown = mapFileModicationTimes.get(changedFile) == null ? 0l
                                    : mapFileModicationTimes.get(changedFile);
                            if (lastModifiedTime != lastModifiedTimeKnown) {
                                mapFileModicationTimes.put(codeFile.getName(), lastModifiedTime);
                                executeScript(codeFile);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Exception while executing script: {}", e);
            }
            watckKey.reset();
        }
    }

    private void executeScript(File codeFile) throws IOException {
        LOG.info("===== Executing groovy script: '{}' ===== ", codeFile.getName());
        StopWatch watch = new StopWatch();
        watch.start();
        Object result = shell.evaluate(codeFile);
        watch.stop();
        LOG.info("===== Execution of groovy script completed in {}ms with result: {} ===== ",
                watch.getTotalTimeMillis(), result);
    }


    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        appContext = ctx;
    }

    private void setGroovyEnvironment() {
        // Add all the defined beans in the groovy bindings so that they can be
        // easily accessible in groovy scripts
        String allBeansNames[] = appContext.getBeanDefinitionNames();
        for (String beanName : allBeansNames) {
            try {
                Object bean = appContext.getBean(beanName);
                mapBindings.put(beanName, bean);
                String aliases[] = appContext.getAliases(beanName);
                // Add all the alias names in groovy env as well
                for (String alias : aliases) {
                    mapBindings.put(alias, bean);
                }
            } catch (Exception e) {
                LOG.error("Unable to find bean reference for {}. It will not be available as variable in groovy script", beanName);
            }
        }
        mapBindings.put("appContext", appContext);

        ImportCustomizer customizer = new ImportCustomizer();
        // customizer.addStarImports("java");
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new CompilationCustomizer[] { customizer });

        shell = new GroovyShell(getClass().getClassLoader(), new Binding(mapBindings), configuration);
        LOG.info("Successfully configured the Groovy environment");
    }

    public static ApplicationContext getApplicationContext() {
        return appContext;
    }

}
