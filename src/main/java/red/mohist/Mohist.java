package red.mohist;

import java.io.File;
import java.util.Scanner;
import org.apache.logging.log4j.Logger;
import red.mohist.configuration.MohistConfigUtil;
import red.mohist.down.DownloadLibraries;
import red.mohist.down.Update;
import red.mohist.forge.FindClassInMod;
import red.mohist.util.ServerEula;
import red.mohist.util.i18n.Message;

public class Mohist {

    public static final String NAME = "Mohist";
    public static final String VERSION = "1.7";
    public static Logger LOGGER;

    public static String getVersion() {
        return Mohist.class.getPackage().getImplementationVersion() != null ? Metrics.class.getPackage().getImplementationVersion() : "unknown";
    }

    public static void main(String[] args) throws Throwable {
        float javaVersion = Float.parseFloat(System.getProperty("java.class.version"));
        if (javaVersion != 52.0) {
            System.out.println(Message.getString("unsupported.java.version"));
            System.exit(0);
        }
        if (System.getProperty("log4j.configurationFile") == null) {
            // Set this early so we don't need to reconfigure later
            System.setProperty("log4j.configurationFile", "log4j2_mohist.xml");
        }
        try {
            FindClassInMod.jar();
        } catch (Exception e) {
            e.printStackTrace();
        }
        MohistConfigUtil.copyMohistConfig();

        ServerEula eula = new ServerEula(new File("eula.txt"));
        if (!eula.hasAcceptedEULA()) {
            System.out.println(Message.getString("eula"));
            Scanner scanner = new Scanner(System.in);
            while (!"true".equals(scanner.next()));
            eula.createEULAFile();
            scanner.close();
        }
        if (Update.isCheck()) {
            Update.hasLatestVersion();
        }
        if (DownloadLibraries.isCheck()) {
            DownloadLibraries.run();
        }
        Class.forName("net.minecraftforge.fml.relauncher.ServerLaunchWrapper").getDeclaredMethod("main", String[].class).invoke(null, new Object[]{args});
    }
}
