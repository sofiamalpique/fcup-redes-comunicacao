import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();
    static private final CharsetEncoder encoder = charset.newEncoder();
    private Socket socket;
    private DataOutputStream toServer;
    private BufferedReader bufFromServer;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {
        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
		    try {
			newMessage(chatBox.getText());
		    } catch (IOException ex) {
		    } finally {
			chatBox.setText("");
		    }
		}
	    });
        frame.addWindowListener(new WindowAdapter() {
		public void windowOpened(WindowEvent e) {
		    chatBox.requestFocus();
		}
	    });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
	try {
	    socket = new Socket(server,port);
	    bufFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	    toServer = new DataOutputStream(socket.getOutputStream());
	}
	catch (IOException e) {
	    System.out.println(e);
	}
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
	try {
	    if (message.length() > 1 && message.charAt(0) == '/' && message.indexOf("nick") != 1 && message.indexOf("join") != 1 && message.indexOf("leave") != 1 && message.indexOf("bye") != 1)
		toServer.write(("/" + message).getBytes());
	    else
		toServer.write(message.getBytes());
	}
	catch (IOException e) {
	    System.out.println(e);
	}
    }
    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
	try {
	    String message;
	    while((message = bufFromServer.readLine()) != null) {
		printMessage(message + "\n");
	    }
	} catch (IOException e) {
	    System.out.println(e);
	}
    }
    
    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
