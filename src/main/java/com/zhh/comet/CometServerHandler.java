package com.zhh.comet;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelFutureProgressListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.example.http.file.HttpStaticFileServerHandler;
import io.netty.handler.codec.frame.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpChunk;
import io.netty.handler.codec.http.DefaultHttpChunkTrailer;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class CometServerHandler  extends SimpleChannelUpstreamHandler {

	 private static final InternalLogger logger =
		        InternalLoggerFactory.getInstance(HttpStaticFileServerHandler.class);

		    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
		    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
		    public static final int HTTP_CACHE_SECONDS = 60;
		    
		    public static Map<String,Channel> allUsers = new ConcurrentHashMap<String,Channel>();
		    
		    public final  static BlockingQueue<Message> msgQueue = new LinkedBlockingQueue<Message>();
		    
		    public static Thread sender;
		    
		    static{
		    	
		    	sender = new Thread(new MessageSender());
		    	sender.setName("Message_Sender");
		    	sender.start();
		    }
		    
		    
		    public CometServerHandler(){
		    	
//		    	MessageSender sender = new MessageSender();
//		    	new Thread(sender).start();
		    }

		    @Override
		    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		        HttpRequest request = (HttpRequest) e.getMessage();
		        if (request.getMethod() != GET) {
		            sendError(ctx, METHOD_NOT_ALLOWED);
		            return;
		        }
		        
		        String uri = request.getUri();
		        if(uri != null && (uri.endsWith(".html") || uri.endsWith(".js") || uri.endsWith(".css") )){
		        	sendFile(request, ctx, e);
		        	return;
		        }
		        
		        if(uri.startsWith("/join")){
		        	join(request,ctx,e);
		        }
		        
		        if(uri.startsWith("/chat")){
		        	chat(request,ctx,e);
		        }
		        
		        if(uri.startsWith("/dingyue")){
		        	
		        }
		        
		        
////		        if(uri.startsWith("/send")){
////		        	send(request,ctx,e);
////		        }
//		       
//		        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
//		        if (isKeepAlive(request)) {
//		            response.setHeader(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
//		        }
//		        response.setHeader("Transfer-Encoding", "chunked");
//		        
//		        Channel ch = e.getChannel();
//                ChannelFuture writeFuture = ch.write(response);
//                
//                String content  = "Hello Comet Server!";
//		        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(content.getBytes()).slice();
//		        
//		        HttpChunk chunk = new DefaultHttpChunk(buffer);
//		        HttpChunk  chunkTrailer = new DefaultHttpChunkTrailer();
//		        ch.write(chunk);
//		        ch.write(chunkTrailer);
//		      
//		        // Decide whether to close the connection or not.
//		        if (!isKeepAlive(request)) {
//		            // Close the connection when the whole content is written out.
//		            writeFuture.addListener(ChannelFutureListener.CLOSE);
//		        }
		    }
		    
		    
		    private static class MessageSender implements Runnable {

				public void run() {
					
				    Message message = null;
				    try{
				    	while((message = msgQueue.take()) != null){
					    	
				    		 Set<Map.Entry<String,Channel>> channels = allUsers.entrySet();
				    		 Iterator<Map.Entry<String,Channel>> iter = channels.iterator();
				    		 while(iter.hasNext()){
				    			 Map.Entry<String, Channel> temp = iter.next();
				    			    final   Channel ch = temp.getValue();
				    			    if(!ch.isConnected()){
				    			    	System.out.println("channel for "  + temp.getKey()  + "  closed");
				    			    	iter.remove();
				    			    	continue;
				    			    }
				    			   ChannelFuture future =  ch.write(wrapMessage(message));
				    			   future.addListener(new ChannelFutureListener() {
									public void operationComplete(ChannelFuture future) throws Exception {
										System.out.println("send complete" );
									}
								});
				    	   // 
				    		 }
					    	
					    }
				    } catch(Exception e){
				    	e.printStackTrace();
				    }finally{
				    	
				    }
				}
				
				private HttpChunk wrapMessage(Message message){
				
					String content = message.toString();
					ChannelBuffer buffer = null;
					try {
						buffer = ChannelBuffers.wrappedBuffer(content.getBytes("UTF-8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
					HttpChunk chunk = new DefaultHttpChunk(buffer);
					return chunk;
					
				}
		    	
		    }
		    
		    public static Map<String,String> handleParameter(String uri){
		    	
		        int startIndex = uri.indexOf("?");
		        Map<String,String> result =  new HashMap<String,String>();
		        if(startIndex == -1 || startIndex == uri.length() - 1)
		        	return result;
		        int flag = 0;//flag 为 获取key
		        StringBuilder key = new StringBuilder();
		        StringBuilder value = new StringBuilder();
		        for(int i = startIndex + 1; i < uri.length();i++){
		        	if(uri.charAt(i) == '='){
		        		flag = 1;
		        	}else if(uri.charAt(i) == '&' || i == uri.length() - 1){
		        		if(i == uri.length() - 1){
		        			if(flag == 0){
			        			key.append(uri.charAt(i));
			        		}else{
			        			value.append(uri.charAt(i));
			        		}
		        		}
		        		flag = 0;
		        		
		        		try {
		        			String v = URLDecoder.decode(value.toString(),"UTF-8");
							result.put(key.toString(), v);
						} catch (UnsupportedEncodingException e) {
							e.printStackTrace();
						}
		        		key = null;
		        		value = null;
		        		key = new StringBuilder();
		        		value = new StringBuilder();
		        	}else{
		        		if(flag == 0){
		        			key.append(uri.charAt(i));
		        		}else{
		        			value.append(uri.charAt(i));
		        		}
		        	}
		        }
		        return result;
		    }
		    
		    public static void main(String[] args){
		    	
		    	Map<String,String> parameter = handleParameter("/join?name=zhang&ab=cd&content=join");
		    	System.out.println(parameter.get("ab"));
		    }
		    
		    private void chat(HttpRequest request,ChannelHandlerContext ctx,MessageEvent e){
		    	
		    	
		    	Message message = new Message();
		    	String uri = request.getUri();
		    	Map<String,String> parameter = handleParameter(uri);
		    	String content = parameter.get("content");
		    	String name = parameter.get("name");
		    	message.setType("chat");
	    		message.setBody(name  + " 说: "  + content);
	    		msgQueue.offer(message);
	    		Channel ch = e.getChannel();
		    	HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
			  //  response.setHeader(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
			    response.setHeader("Transfer-Encoding", "chunked");
			    response.setHeader("Content-Type", "text/html; charset=utf-8");
			    response.setHeader("Server", "Netty Comet 1.0");
			    ch.write(response);
		        String c = "ok";
			    ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(c.getBytes());
			    HttpChunk chunk = new DefaultHttpChunk(buffer);
			    ch.write(chunk);
			    ChannelFuture writeFuture = ch.write(new DefaultHttpChunkTrailer());
			    writeFuture.addListener(ChannelFutureListener.CLOSE);
		    }
		    
		    @Override
			public void channelClosed(ChannelHandlerContext ctx,
					ChannelStateEvent e) throws Exception {
				
				super.channelClosed(ctx, e);
				Channel ch = e.getChannel();
				if(allUsers.containsValue(ch)){
					String name = (String)ch.getAttachment();
					Message leavelMsg = new Message();
			    	leavelMsg.setBody( name + " 离开了，欢迎再来");
			    	leavelMsg.setType("chat");
			    	msgQueue.offer(leavelMsg);
					System.out.println("channel" + ch + "closed");
				}
			}

			private void join(HttpRequest request,ChannelHandlerContext ctx, MessageEvent e){
		    	
		    	Message message = new Message();
		    	String uri = request.getUri();
		    	Map<String,String> parameter = handleParameter(uri);
		    	String content = parameter.get("content");
		    	String name = parameter.get("name");
		    	if("join".equals(content)){
		    		message.setType("join");
		    		message.setBody(name + "  加入聊天中^-^");
				    Channel ch = e.getChannel();
		            allUsers.put(name, ch);
		    	}else{
		    		message.setType("chat");
		    		message.setBody(name  + "  说: "  + content);
		    	}
		    	Channel ch = e.getChannel();
		    	ch.setAttachment(name);
		    	HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
			    response.setHeader(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
			    response.setHeader("Transfer-Encoding", "chunked");
			    response.setHeader("Content-Type", "text/html; charset=utf-8");
			    response.setHeader("Server", "Netty Comet 1.0");
			    ch.write(response);
		    	msgQueue.offer(message);
		    }

		    @Override
		    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		            throws Exception {
		        Channel ch = e.getChannel();
		        Throwable cause = e.getCause();
		        if (cause instanceof TooLongFrameException) {
		            sendError(ctx, BAD_REQUEST);
		            return;
		        }

		        cause.printStackTrace();
		        if (ch.isConnected()) {
		            sendError(ctx, INTERNAL_SERVER_ERROR);
		        }
		    }

		    private String sanitizeUri(String uri) {
		        // Decode the path.
		        try {
		            uri = URLDecoder.decode(uri, "UTF-8");
		        } catch (UnsupportedEncodingException e) {
		            try {
		                uri = URLDecoder.decode(uri, "ISO-8859-1");
		            } catch (UnsupportedEncodingException e1) {
		                throw new Error();
		            }
		        }

		        // Convert file separators.
		        uri = uri.replace('/', File.separatorChar);

		        // Simplistic dumb security check.
		        // You will have to do something serious in the production environment.
		        if (uri.contains(File.separator + ".") ||
		            uri.contains("." + File.separator) ||
		            uri.startsWith(".") || uri.endsWith(".")) {
		            return null;
		        }

		        // Convert to absolute path.
		        return System.getProperty("udir") + File.separator + uri;
		    }

		    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
		        response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
		        response.setContent(ChannelBuffers.copiedBuffer(
		                "Failure: " + status.toString() + "\r\n",
		                CharsetUtil.UTF_8));

		        // Close the connection as soon as the error message is sent.
		        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
		    }
		    
		    /**
		     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
		     * 
		     * @param ctx
		     *            Context
		     */
		    private void sendNotModified(ChannelHandlerContext ctx) {
		        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
		        setDateHeader(response);

		        // Close the connection as soon as the error message is sent.
		        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
		    }
		    
		    /**
		     * Sets the Date header for the HTTP response
		     * 
		     * @param response
		     *            HTTP response
		     */
		    private void setDateHeader(HttpResponse response) {
		        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		        Calendar time = new GregorianCalendar();
		        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
		    }
		    
		    private void sendFile(HttpRequest request,ChannelHandlerContext ctx,MessageEvent e) throws Exception {
		    	
		    	final String path = sanitizeUri(request.getUri());
		        if (path == null) {
		            sendError(ctx, FORBIDDEN);
		            return;
		        }

		        File file = new File(path);
		        if (file.isHidden() || !file.exists()) {
		            sendError(ctx, NOT_FOUND);
		            return;
		        }
		        if (!file.isFile()) {
		            sendError(ctx, FORBIDDEN);
		            return;
		        }

		        // Cache Validation
		        String ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
		        if (ifModifiedSince != null && !ifModifiedSince.equals("")) {
		            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

		            // Only compare up to the second because the datetime format we send to the client does not have milliseconds 
		            long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
		            long fileLastModifiedSeconds = file.lastModified() / 1000;
		            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
		                sendNotModified(ctx);
		                return;
		            }
		        }
		        
		        RandomAccessFile raf;
		        try {
		            raf = new RandomAccessFile(file, "r");
		        } catch (FileNotFoundException fnfe) {
		            sendError(ctx, NOT_FOUND);
		            return;
		        }
		        long fileLength = raf.length();

		        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		        setContentLength(response, fileLength);
		       if(request.getUri().endsWith("html")){
		    	   response.setHeader("Content-Type", "text/html");
		       }else if(request.getUri().endsWith("js")){
		    	   response.setHeader("Content-Type", "application/x-javascript");
		       }else if(request.getUri().endsWith("js")){
		    	   response.setHeader("Content-Type", "text/css");
		       }
		        setDateAndCacheHeaders(response, file);
		        if (isKeepAlive(request)) {
		            response.setHeader(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		        }
		        
		        Channel ch = e.getChannel();

		        // Write the initial line and the header.
		        ch.write(response);

		        // Write the content.
		        ChannelFuture writeFuture;
		        if (ch.getPipeline().get(SslHandler.class) != null) {
		            // Cannot use zero-copy with HTTPS.
		            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
		        } else {
		            // No encryption - use zero-copy.
		            final FileRegion region =
		                new DefaultFileRegion(raf.getChannel(), 0, fileLength);
		            writeFuture = ch.write(region);
		            writeFuture.addListener(new ChannelFutureProgressListener() {
		                public void operationComplete(ChannelFuture future) {
		                    region.releaseExternalResources();
		                }
		                public void operationProgressed(
		                        ChannelFuture future, long amount, long current, long total) {
		                    logger.info(String.format("%s: %d / %d (+%d)%n", path, current, total, amount));
		                }
		            });
		        }
		    }
		    
		    /**
		     * Sets the Date and Cache headers for the HTTP Response
		     * 
		     * @param response
		     *            HTTP response
		     * @param fileToCache
		     *            file to extract content type
		     */
		    private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
		        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		        // Date header
		        Calendar time = new GregorianCalendar();
		        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

		        // Add cache headers
		        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		        response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
		        response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
		        response.setHeader(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
		    }
		    
//		    private void setContentTypeHeader(HttpResponse response, File file) {
//		        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
//		        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
//		    }

	
}
