
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class HTTPRequestHandler implements Runnable {

	public static enum RequestMethod {
		GET("GET"),
		HEAD("HEAD"),
		POST("POST"),
		PUT("PUT"),
		DELETE("DELETE"),
		TRACE("TRACE"),
		CONNECT("CONNECT"),
		OTHERS(null);

		private final String method;

		RequestMethod(String method) {
			this.method = method;
		}
		
		@Override
		public String toString() {
			return method;
		}
	}
	
	public static enum ResponseCode {
		_200("200 OK"),
		_301("301 Moved Permanently"), 
		_304("304 Not Modified"),
		_400("400 Bad Request", "The request message is not understood by server, please try again."),
		_403("403 Forbidden", "You don't have permission to access * on this server."),
		_404("404 Not Found", "This website * is not available or the file is missing on this server."),
		_405("405 Method Not Allowed", "Method * is not allowed in this server."),
		_500("500 Internal Server Error", "The server encounters an error, please try again later."),
		_501("501 Not Implemented", "This request method * is not supported by the server."),
		_505("505 HTTP Version not supported", "HTTP Version * is not supported");

		private final String code;
		private final String message;

		ResponseCode(String code, String message) {
			this.code = code;
			this.message = message;
		}
		
		ResponseCode(String code) {
			this.code = code;
			this.message = "";
		}

		@Override
		public String toString() {
			return code;
		}
		
		public String getMessage() {
			return message;
		}
	}
	
	public static enum ContentType {
		HTML("text/html"),
		CSS("text/css"),
		JS("application/javascript"),
		JPG("image/jpeg"),
		GIF("image/gif"),
		ICO("image/x-icon"),
		PNG("image/png"),
		PDF("application/pdf"),
		DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
		XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
		PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
		XML("application/xml"),
		ZIP("application/zip"),
		OTHERS(null);
		
		private final String type;

		ContentType(String type) {
			this.type = type;
		}
		
		@Override
		public String toString() {
			return type;
		}
	}
	
	public static final String HTTP_VERSION = "HTTP/1.1";
	public static final String SERVER_NAME = "COMP4621HttpServer";
	
	private Socket socket;

	boolean gzip = false;
	RequestMethod method;
	ResponseCode code;
	String uri;
	String httpVersion;
	List<String> requestHeaders = new ArrayList<String>();
	List<String> responseHeaders = new ArrayList<String>();
	byte[] responseBody;
	
	public HTTPRequestHandler(Socket socket) {
		this.socket = socket;
	}
	
	@Override
	public void run() {
		try {
			if(handleRequest()) {
				if(handleResponse()) {
					respond();
				}
			}
			socket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private boolean handleRequest() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		String requestStr = reader.readLine();
		// split the first line to meaningful information
		// e.g. GET /index.html HTTP/1.1\r\n
		if (requestStr != null) {
			System.out.println("-----------Request-----------");
			System.out.println(requestStr);
			String[] requestInfo = requestStr.split("\\s+");
			try {
				method = RequestMethod.valueOf(requestInfo[0]);
			} catch (Exception e) {
				method = RequestMethod.OTHERS;
			}
			uri = requestInfo[1];
			httpVersion = requestInfo[2];
			// get all request headers
			while (!requestStr.equals("")) {
				requestStr = reader.readLine();
				System.out.println(requestStr);
				if (!gzip && requestStr.contains("Accept-Encoding")) {
					gzip = requestStr.contains("gzip");
				}
				requestHeaders.add(requestStr);
			}
			return true;
		}
		return false;
	}
	
	private boolean handleResponse() throws IOException {
		if (method == null)
			return false;
		if (!HTTP_VERSION.equals(httpVersion)) {
			setReponseHeader(ResponseCode._505);
			setErrorPage(httpVersion);
			return false;
		}
		switch (method) {
		case HEAD:
			setReponseHeader(ResponseCode._200);
			break;
		case GET:
			try {
				setReponseHeader(ResponseCode._200);
				File file = new File("." + uri);
				if (file.isDirectory()) {
					setReponseHeader(ResponseCode._403);
					setErrorPage(uri);
				}
				else if (file.exists()) {
					String fileType = uri.substring(uri.lastIndexOf(".") + 1);
					responseHeaders.add("Content-Type: " + ContentType.valueOf(fileType.toUpperCase()).toString());
					responseHeaders.add("Date: " + new Date(System.currentTimeMillis()));
					responseHeaders.add("Last-Modified: " + new Date(file.lastModified()));
					setResponseBody(Files.readAllBytes(file.toPath()));
				}
				else {
					setReponseHeader(ResponseCode._404);
					setErrorPage(uri);
				}
			}
			catch (Exception e) {
				setReponseHeader(ResponseCode._400);
				setErrorPage("");
			}
			break;
		case POST:
			setReponseHeader(ResponseCode._405);
			setErrorPage("POST");
			break;
		case OTHERS:
			setReponseHeader(ResponseCode._400);
			setErrorPage("");
			break;
		default:
			setReponseHeader(ResponseCode._501);
			setErrorPage(method.toString());
		}
		return true;
	}
	
	private void setReponseHeader(ResponseCode code) {
		this.code = code;
		responseHeaders.add(HTTP_VERSION + " " + code.toString());
		responseHeaders.add("Connection: close");
		responseHeaders.add("Server: " + SERVER_NAME);
	}
	
	private void setErrorPage(String replaceMsg) {
		String errorPageString = "";
		String fullCodeStr = code.toString();
		String codeStr = fullCodeStr.substring(fullCodeStr.indexOf(" ") + 1);
		errorPageString += "<HTML><HEAD>\r\n";
		errorPageString += "<TITLE>" + fullCodeStr + "</TITLE>\r\n";
		errorPageString += "</HEAD><BODY>\r\n";
		errorPageString += "<H1>" + codeStr + "</H1>\r\n";
		String errMsg = code.getMessage();
		if (errMsg.contains("*")) {
			errMsg = errMsg.replace("*", replaceMsg);
		}
		errorPageString += "<P>" + errMsg + "</P>\r\n<HR>\r\n";
		errorPageString += "<P><I>" + SERVER_NAME + " at localhost Port " + socket.getLocalPort() + "</I></P>\r\n";
		errorPageString += "</BODY><HTML>\r\n";
		responseBody = errorPageString.getBytes();
	}
	
	private void setResponseBody(byte[] response) {
		responseBody = response;
	}
	
	private void respond() throws IOException {
		if (gzip) {
			responseBody = compressFile();
			responseHeaders.add("Content-encoding: gzip");
		}
		responseHeaders.add("Transfer-Encoding: chunked");
		ChunkedOutputStream output = new ChunkedOutputStream(socket.getOutputStream());
		for (String header : responseHeaders) {
			System.out.println(header);
			output.write(header);
		}		
		output.pushCRLF();
		if (responseBody != null) {
			output.write(responseBody);
		}
		output.finish();
		output.flush();
		output.close();
	}
	
	private byte[] compressFile() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();  
        GZIPOutputStream gzip;
        try {
	        gzip = new GZIPOutputStream(out);  
	        gzip.write(responseBody);  
	        gzip.close();
        } 
        catch (IOException e) {
        	System.out.println("Error when compressing file");
        }
        return out.toByteArray();  
	}

}
