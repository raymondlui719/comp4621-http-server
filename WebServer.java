
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer extends Thread {

	private static final int DEFAULT_PORT = 8080;

	private static final int N_THREADS = 10;
	
	public static void main(String[] args) {
		int port = DEFAULT_PORT;
		WebServer webserver = new WebServer();
		try {
			if (args.length > 0) {
				port = Integer.parseInt(args[0]);
				if (port <= 0 || port >= 65535) {
					throw new NumberFormatException();
				}
			}
		}
		catch (NumberFormatException ex) {
			port = DEFAULT_PORT;
			System.out.println("Invalid port number (1-65534), trying to start the server with default port 8080");
		}
		finally {
			try {
				webserver.start(port);
			} catch (IOException e) {
				System.out.println("Unexpected error: " + e.getMessage());
			}
		}
	}
	
	@SuppressWarnings("resource")
	public void start(int port) throws IOException {
		ServerSocket s = new ServerSocket(port);
		System.out.println("Web server listening on port " + port + " (press CTRL-C to quit)");
		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
		while (true) {
			executor.submit(new HTTPRequestHandler(s.accept()));
		}
	}

}
