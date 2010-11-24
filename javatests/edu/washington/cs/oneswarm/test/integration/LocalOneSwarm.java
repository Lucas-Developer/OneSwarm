package edu.washington.cs.oneswarm.test.integration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.FileUtil;

import edu.washington.cs.oneswarm.test.util.ProcessLogConsumer;

/**
 * Encapsulates a locally running testing instance of OneSwarm. Each instance of this class
 * corresponds to a separate process running on the local machine.
 *
 * This class should only be used by integration tests.
 */
public class LocalOneSwarm {

	// Used to automatically choose instance labels.
	private static int instanceCount = 0;

	/** The set of listeners. */
	List<LocalOneSwarmListener> listeners = new ArrayList<LocalOneSwarmListener>();

	/** The configuration of this instance. */
	LocalOneSwarm.Config config = new LocalOneSwarm.Config();

	/** The running OneSwarm process. */
	Process process = null;

	/** The root path from which we're running tests. Paths are constructed relative to this. */
	String rootPath = null;

	/** Configuration parameters for the local instance. */
	public class Config {

		/** The label for this instance. */
		String label = "LocalOneSwarm";

		/** The path to system java. */
		String javaPath = "/usr/bin/java";

		/** The path to the GWT war output directory. */
		String warRootPath;

		/** The port on which the local webserver listens for GUI connections. */
		int webUiPort;

		/** Classpath elements for the invocation. */
		List<String> classPathElements = new ArrayList<String>();

		public String getLabel() { return label; }
		public void setLabel(String label) { this.label = label; }

		public String getWarRootPath() { return warRootPath; }
		public void setWarRootPath(String path) { warRootPath = path; }

		public List<String> getClassPathElements() { return classPathElements; }
		public void addClassPathElement(String path) { classPathElements.add(path); }

		public int getWebUiPort() { return webUiPort; }
		public void setWebUiPort(int port) { webUiPort = port; }
	}

	public LocalOneSwarm() {
		rootPath = new File(".").getAbsolutePath();

		config.setLabel("LocalOneSwarm-" + instanceCount);

		// 2 * instanceCount since the client uses the local port + 1 for SSL/remote access.
		config.setWebUiPort(2000 + (2 * instanceCount));

		instanceCount++;

		/* TODO(piatek): Remove user-specific paths here. */
		config.setWarRootPath("gwt-bin/war");
		config.addClassPathElement("eclipse-bin");

		/* SWT */
		String swt = "build/swt/";
		if (Constants.isOSX) {
			swt += "swt-osx-cocoa-x86_64.jar";
		} else if (Constants.isLinux) {
			if (System.getProperty("sun.arch.data.model").equals("32")) {
				swt += "swt-gtk-linux-x86.jar";
			} else {
				swt += "swt-gtk-linux-x86_64.jar";
			}
		} else if (Constants.isWindows) {
			swt += "swt-win32-x86.jar";
		}
		config.addClassPathElement(swt);

		/* Other dependencies */
		final String COMMONS = "build/gwt-libs/commons-http/";
		final String GWT = "build/gwt-libs/";
		final String F2F = "build/f2f-libs/";
		final String CORE = "build/core-libs/";
		String [] deps = {

			// Apache commons
			COMMONS + "jcip-annotations.jar",
			COMMONS + "httpmime-4.0.jar",
			COMMONS + "httpcore-4.0.1.jar",
			COMMONS + "httpclient-4.0.jar",
			COMMONS + "commons-logging-1.1.1.jar",
			COMMONS + "commons-io-1.3.2.jar",
			COMMONS + "commons-fileupload-1.2.1.jar",
			COMMONS + "commons-codec-1.4.jar",
			COMMONS + "apache-mime4j-0.6.jar",

			// GWT Core
			GWT + "gwt/gwt-user.jar",
			GWT + "gwt/gwt-servlet.jar",
			GWT + "gwt/gwt-dev.jar",
			// GWT Libs
			GWT + "gwt-incubator/gwt-incubator.jar",
			GWT + "gwt-dnd/gwt-dnd-2.6.5.jar",
			// TODO(piatek): Move this?
			GWT + "jaudiotagger.jar",
			// Jetty
			GWT + "jetty/jetty.jar",
			GWT + "jetty/jetty-util.jar",
			GWT + "jetty/jetty-servlet-api.jar",
			GWT + "jetty/jetty-management.jar",

			// F2F Libs
			F2F + "smack.jar",
			F2F + "publickey-client.jar",
			F2F + "ecs-1.4.2.jar",

			// Core libs
			CORE + "derby.jar",
			CORE + "log4j.jar",
			CORE + "junit.jar",
			CORE + "commons-cli.jar",
			CORE + "apple-extensions.jar",
		};

		for (String dep : deps) {
			config.addClassPathElement(dep);
		}
	}

	/** Adds {@code listener} to the set of listeners. */
	public void addListener(LocalOneSwarmListener listener) {
		listeners.add(listener);
	}

	/** Removes {@code listener} from the set of listeners. */
	public void removeListener(LocalOneSwarmListener listener) {
		listeners.remove(listener);
	}

	/** Asynchronously starts the process associated with this instance. */
	public void start() throws IOException {

		// Construct a ProcessBuilder with common options
		ProcessBuilder pb = new ProcessBuilder(config.javaPath,
			"-Xmx256m",
			"-Ddebug.war=" + new File(rootPath, config.warRootPath),
			"-Dazureus.security.manager.install=0",
			"-DMULTI_INSTANCE=true");

		List<String> command = pb.command();

		// Add platform-specific options
		if (Constants.isOSX) {
			command.add("-XstartOnFirstThread");
		}

		// Add classpath
		StringBuilder cpString = new StringBuilder();
		for (String path : config.classPathElements) {
			cpString.append(rootPath + "/" + path);
			cpString.append(':');
		}
		command.add("-cp");
		// -1 because of the spurious ':' at the end
		command.add(cpString.substring(0, cpString.length()-1));

		// Configure system properties for test instances
		Map<String, String> scratchPaths = new HashMap<String, String>();
		for (String dir : new String[]{"userData", "workingDir"}) {
			File tmpDir = new File(System.getProperty("java.io.tmpdir"), config.getLabel() +
					"-" + dir);
			FileUtil.recursiveDelete(tmpDir);
			FileUtil.mkdirs(tmpDir);

			scratchPaths.put(dir, tmpDir.getAbsolutePath());

			// XXX: use proper logging
			System.out.println(config.getLabel() + " " + dir + ": " + tmpDir.getAbsolutePath());
		}

		command.add("-Doneswarm.integration.test=1");
		command.add("-Doneswarm.integration.user.data=" + scratchPaths.get("userData"));
		command.add("-Doneswarm.integration.web.ui.port=" + config.getWebUiPort());

		// Main class
		command.add("com.aelitis.azureus.ui.Main");

		// Kick-off: merge stderr and stdout, set the working directory, and start.
		pb.redirectErrorStream(true);
		pb.directory(new File(scratchPaths.get("workingDir")));
		process = pb.start();

		// Consume the unified log.
		new ProcessLogConsumer(config.label, process).start();

		// XXX: debug
		try {
			System.out.println("output status code: " + process.waitFor());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/** Asynchronously stops the process associated with this instance. */
	public void stop() {

	}

	/** Used for debugging. */
	public static void main(String [] args) throws Exception {
		new LocalOneSwarm().start();

		while(true) {
			Thread.sleep(100);
		}
	}
}
