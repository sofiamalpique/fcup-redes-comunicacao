import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer
{
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private final CharsetEncoder encoder = charset.newEncoder();
    
    static private final HashMap<String,Set<SocketChannel>> mapRoom = new HashMap<String,Set<SocketChannel>>(1024);
    static private final HashMap<SocketChannel,String> mapSockName = new HashMap<SocketChannel,String>(1024);
    static private final HashMap<SocketChannel,String> mapSockRoom = new HashMap<SocketChannel,String>(1024);
    static private final HashMap<String,Integer> mapAvailNames = new HashMap<String,Integer>(1024);
    
    static public void main( String args[] ) throws Exception {
	// Parse port from command line
	int port = Integer.parseInt( args[0] );
	try {
	    // Instead of creating a ServerSocket, create a ServerSocketChannel
	    ServerSocketChannel ssc = ServerSocketChannel.open();

	    // Set it to non-blocking, so we can use select
	    ssc.configureBlocking( false );

	    // Get the Socket connected to this channel, and bind it to the
	    // listening port
	    ServerSocket ss = ssc.socket();
	    InetSocketAddress isa = new InetSocketAddress( port );
	    ss.bind( isa );

	    // Create a new Selector for selecting
	    Selector selector = Selector.open();

	    // Register the ServerSocketChannel, so we can listen for incoming
	    // connections
	    ssc.register( selector, SelectionKey.OP_ACCEPT );
	    System.out.println( "Listening on port "+port );

	    while (true) {
		// See if we've had any activity -- either an incoming connection,
		// or incoming data on an existing connection
		int num = selector.select();

		// If we don't have any activity, loop around and wait again
		if (num == 0) {
		    continue;
		}

		// Get the keys corresponding to the activity that has been
		// detected, and process them one by one
		Set<SelectionKey> keys = selector.selectedKeys();
		Iterator<SelectionKey> it = keys.iterator();
		while (it.hasNext()) {
		    // Get a key representing one of bits of I/O activity
		    SelectionKey key = it.next();

		    // What kind of activity is it?
		    if (key.isAcceptable()) {

			// It's an incoming connection.  Register this socket with
			// the Selector so we can listen for input on it
			Socket s = ss.accept();
			System.out.println( "Got connection from "+s );

			// Make sure to make it non-blocking, so we can use a selector
			// on it.
			SocketChannel sc = s.getChannel();
			sc.configureBlocking( false );

			// Register it with the selector, for reading
			sc.register( selector, SelectionKey.OP_READ );

		    } else if (key.isReadable()) {

			SocketChannel sc = null;

			try {

			    // It's incoming data on a connection -- process it
			    sc = (SocketChannel)key.channel();
			    boolean ok = processInput( sc );

			    // If the connection is dead, remove it from the selector
			    // and close it
			    if (!ok) {
				
				Socket s = null;
				try {

				    if (mapSockRoom.containsKey(sc)) {
					String name = mapSockName.get(sc);
					Set<SocketChannel> set = mapRoom.get(mapSockRoom.get(sc));
					mapSockRoom.remove(sc);
					set.remove(sc);
					for (SocketChannel scit : set) {
					    scit.write(encoder.encode(CharBuffer.wrap("LEFT " + name + "\n")));
					}			    
				    }
				    if (mapSockName.containsKey(sc)){
					if (mapAvailNames.containsKey(mapSockName.get(sc))) {
					    mapAvailNames.remove(mapSockName.get(sc));
					}
					mapSockName.remove(sc);
				    }

				    key.cancel();
				    s = sc.socket();
				    System.out.println( "Closing connection to "+s );
				    s.close();
				} catch( IOException ie ) {
				    System.err.println( "Error closing socket "+s+": "+ie );
				}
			    }

			} catch( IOException ie ) {

			    // On exception, remove this channel from the selector
			    key.cancel();

			    try {
				sc.close();
			    } catch( IOException ie2 ) { System.out.println( ie2 ); }

			    System.out.println( "Closed "+sc );
			}
		    }
		}

		// We remove the selected keys, because we've dealt with them.
		keys.clear();
	    }
	} catch( IOException ie ) {
	    System.err.println( ie );
	}
    }


    // Just read the message from the socket and send it to stdout
    static private boolean processInput( SocketChannel sc ) throws IOException {
	// Read the message to the buffer
	String majorMessage;
	buffer.clear();
	sc.read( buffer );
	buffer.flip();
	majorMessage = decoder.decode(buffer).toString();

	// If no data, close the connection
	if (buffer.limit()==0) {
	    return false;
	}

	while (majorMessage.charAt(majorMessage.length()-1) != '\n') {
	    buffer.clear();
	    sc.read(buffer);
	    buffer.flip();
	    majorMessage += decoder.decode(buffer).toString();    
	}
	
	System.out.print( majorMessage );
	
	String[] messages = majorMessage.split("[\n]");
	for (String message : messages) {
	    CharBuffer tmpbuf = CharBuffer.allocate(1048576);
	    boolean mess = false;
	    boolean error = true;
	    String[] decmessage = message.split("[ \t\n\f\r]");
	    int beg = 0;

	    if (message.length() > 1 && message.charAt(0) == '/' && message.charAt(1) == '/')
		beg = 1;
	
	    if (message.length() > 1) {
		mess = true;
		if (message.charAt(0) == '/') {
		    if (message.charAt(1) != '/') {
			mess = false;
			if (message.indexOf("nick") == 1 && decmessage.length == 2 && !mapAvailNames.containsKey(decmessage[1])) {
			    String newer = decmessage[1];
			    if ( mapSockRoom.containsKey(sc) ) {
				String older = mapSockName.get(sc);
				for(SocketChannel scit : mapRoom.get(mapSockRoom.get(sc))) {
				    if (sc != scit)
					scit.write(encoder.encode(CharBuffer.wrap("NEWNICK " + older + " " + newer + "\n")));
				}
			    }
			    if (mapSockName.containsKey(sc)) {
				mapAvailNames.remove(mapSockName.get(sc));
				mapSockName.remove(sc);
			    }
			    mapSockName.put(sc,newer);
			    mapAvailNames.put(newer,1);
			    sc.write(encoder.encode(CharBuffer.wrap("OK\n")));
			    error = false;
			} else if (message.indexOf("join") == 1 && decmessage.length == 2 && mapSockName.containsKey(sc)) {
			    String name = mapSockName.get(sc);
			    String newer = decmessage[1];
			    sc.write(encoder.encode(CharBuffer.wrap("OK\n")));
			    if (mapSockRoom.containsKey(sc)) {
				for (SocketChannel scit : mapRoom.get(mapSockRoom.get(sc))) {
				    if (sc != scit)
					scit.write(encoder.encode(CharBuffer.wrap("LEFT " + name + "\n")));
				}
				String older = mapSockRoom.get(sc);
				mapRoom.get(older).remove(sc);
				if (mapRoom.get(older).size() == 0){
				    mapRoom.remove(older);
				}
				mapSockRoom.remove(sc);
			    }
			    mapSockRoom.put(sc,newer);
			    if (mapRoom.containsKey(newer)) {
				mapRoom.get(newer).add(sc);
			    } else {
				mapRoom.put(newer, new HashSet<SocketChannel>());
				mapRoom.get(newer).add(sc);
			    }
			    for (SocketChannel scit : mapRoom.get(newer)) {
				if (sc != scit)
				    scit.write(encoder.encode(CharBuffer.wrap("JOINED " + name + "\n")));
			    }
			    error = false;
			} else if (message.indexOf("leave") == 1 && decmessage.length == 1 && mapSockRoom.containsKey(sc) && mapSockName.containsKey(sc)){
			    String name = mapSockName.get(sc);
			    for (SocketChannel scit : mapRoom.get(mapSockRoom.get(sc))) {
				if (sc != scit)
				    scit.write(encoder.encode(CharBuffer.wrap("LEFT " + name + "\n")));
			    }
			    String older = mapSockRoom.get(sc);
			    mapSockRoom.remove(sc);
			    mapRoom.get(older).remove(sc);
			    if (mapRoom.get(older).size() == 0){
				mapRoom.remove(older);
			    }
			    sc.write(encoder.encode(CharBuffer.wrap("OK\n")));
			    error = false;
			} else if (message.indexOf("bye") == 1 && decmessage.length == 1) {
			    sc.write(encoder.encode(CharBuffer.wrap("BYE\n")));
			    if (mapSockRoom.containsKey(sc)) {
				Set<SocketChannel> s = mapRoom.get(mapSockRoom.get(sc));
				mapSockRoom.remove(sc);
				s.remove(sc);
				String name = mapSockName.get(sc);
				for (SocketChannel scit : s) {
				    scit.write(encoder.encode(CharBuffer.wrap("LEFT " + name + "\n")));
				}
			    
			    }
			    if (mapSockName.containsKey(sc)){
				if (mapAvailNames.containsKey(mapSockName.get(sc))) {
				    mapAvailNames.remove(mapSockName.get(sc));
				}
				mapSockName.remove(sc);
			    }
			    sc.close();
			    error = false;
			}
		    }
		}
	    }

	    if (mess && mapSockRoom.containsKey(sc)) {
		error = false;
		for (SocketChannel scit : mapRoom.get(mapSockRoom.get(sc))) {
		    scit.write(encoder.encode(CharBuffer.wrap("MESSAGE " + mapSockName.get(sc) + " " + message.substring(beg,message.length()) + "\n")));
		}
	    }

	    if (error) {
		sc.write(encoder.encode(CharBuffer.wrap("ERROR\n")));
	    }
	}

	return true;
    }
}
