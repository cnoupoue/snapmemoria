package be.cnoupoue.memoriavault;

import be.cnoupoue.memoriavault.browser.ExistingInstanceStartupListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MemoriaVaultApplication {

  static final String DESKTOP_MODE_PROPERTY = "memoriavault.desktop";
  private static final String DESKTOP_APPLICATION_CLASS =
      "be.cnoupoue.memoriavault.MemoriaVaultDesktopApplication";

  private MemoriaVaultApplication() {}

  public static void main(String[] args) {
    if (Boolean.getBoolean(DESKTOP_MODE_PROPERTY)) {
      launchDesktop(args);
      return;
    }

    startBackend(args, true, true);
  }

  static ConfigurableApplicationContext startBackend(
      String[] args, boolean headless, boolean enableExistingInstanceCheck) {
    SpringApplication application = new SpringApplication(MemoriaVaultApplication.class);
    if (enableExistingInstanceCheck) {
      application.addListeners(new ExistingInstanceStartupListener());
    }
    application.setHeadless(headless);
    return application.run(args);
  }

  private static void launchDesktop(String[] args) {
    try {
      Class<?> desktopApplication = Class.forName(DESKTOP_APPLICATION_CLASS);
      Method launchDesktop = desktopApplication.getDeclaredMethod("launchDesktop", String[].class);
      launchDesktop.setAccessible(true);
      launchDesktop.invoke(null, (Object) args);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(
          "Desktop mode requires building with the windows-desktop Maven profile.", exception);
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new IllegalStateException("Desktop mode is not correctly configured.", exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Desktop mode failed to start.", cause);
    }
  }
}
