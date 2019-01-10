package main;

import java.net.*;
import java.io.*;
import java.util.*;

public class Leiloeiro extends ClientLeiloeiro {
    private static final boolean DEFAULT_DEBUG_VALUE = true;
    
    private static Map<String,Item> itemsVenda = new HashMap<String,Item>();
    private static int numeroDeThreads = 0;
    private static Map<String,Runnable> acoesAguardoConfirmacao = new HashMap<String,Runnable>();
    private static HashMap<String,InetSocketAddress> clientesRegistradosBroadcast = new HashMap<String,InetSocketAddress>();
    
    private static ThreadLocal clientId = new ThreadLocal();
    private static String getClientId() { return (String) clientId.get(); }
    private static void setClientId(String clientId) { Leiloeiro.clientId.set(clientId); }

    private static ThreadLocal socketInArmazenamento = new ThreadLocal();
    private static BufferedReader getSocketIn() { return (BufferedReader) socketInArmazenamento.get(); }
    private static void setSocketIn(BufferedReader reader) { socketInArmazenamento.set(reader); }
    
    private static ThreadLocal socketOutArmazenamento = new ThreadLocal();
    private static PrintWriter getSocketOut() { return (PrintWriter) socketOutArmazenamento.get(); }
    private static void setSocketOut(PrintWriter writer) { socketOutArmazenamento.set(writer); }
    
    /*
    ** CONSTRUTOR
    */
    public Leiloeiro(int tcpPortNumber) throws IOException {
        
        ServerSocket serverSocket;
        serverSocket = new ServerSocket(tcpPortNumber);
        
        for (;;) {
            final Socket ioSocket = serverSocket.accept();
            
            Thread thread = new Thread(){
                int threadId = ++numeroDeThreads;
                
                public void run(){
                    try {
                        System.out.println("Conexao aceita.");
                        setSocketOut(new PrintWriter(ioSocket.getOutputStream(), true));
                        setSocketIn(new BufferedReader(new InputStreamReader(ioSocket.getInputStream())));
                        
                        String firstLine = getSocketIn().readLine();
                        
                        if (!firstLine.trim().matches("OLA " + Protocol.PROTOCOL_NAME_AND_VERSION)){
                            respond("ERROR Protocolo errado.");
                            ioSocket.close();
                        }
                        
                        Map<String,String> labelValuePairs;
                        
                        try {
                            labelValuePairs = lerDoClient();
                        } catch (Exception e) {
                            respond("ERROR Entrada errada.");
                            return;
                        }
                        
                        if (!labelValuePairs.containsKey("ID")) {
                            respond("ERROR Cliente precisa ter um ID");
                        }
                        else{
                            setClientId(labelValuePairs.get("ID"));
                        }
                        
                        InetAddress address = ioSocket.getInetAddress();
                        
                        int port = Protocol.DEFAULT_PORT_NUMBER;
                        if (labelValuePairs.containsKey("UDP")) {
                            port = Integer.parseInt(labelValuePairs.get("UDP"));
                        }
                        
                        if (port >= 1 && port <= 65535) {
                            log("Registrando os clientes para broadcasts...");
                            synchronized(clientesRegistradosBroadcast){   
                                clientesRegistradosBroadcast.put(getClientId(), new InetSocketAddress(address, port));
                            }
                            log("... feito.");
                        }
                        respond("OK " + Protocol.PROTOCOL_NAME_AND_VERSION);
                        
                        commandLoop();
                        
                        ioSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            thread.start();
        }
    }    
    
    private static Thread checarItensExpirados = new Thread() {
        
        public void run() {
            for (;;) {
                List<Item> itensExpirados = new ArrayList<Item>();;
                Date now = new Date();
                
                synchronized(itemsVenda) {
                    if (itemsVenda != null) {
                        
                        for (Item item : itemsVenda.values()) {
                            if (!item.finalizado() && item.getLeilaoTempoFim().compareTo(now) < 0) {
                                item.setFinalizado(true);
                                itemsVenda.put(item.getId(), item);
                                itensExpirados.add(item);
                            }
                        }
                    }
                }

                for (Item item : itensExpirados) {
                    String vendedor = item.getVendedorId();
                    String compradorVencendoLance = item.getCompradorVencendoLanceId();
                    String itemId = item.getId();
                    String itemNome = item.getNome();
                    
                    if (compradorVencendoLance != null) {
                        broadcastToAllClients("O comprador "+compradorVencendoLance+ " venceu o lance");
                        sendToSingleClient(compradorVencendoLance, "Voce ganhou " + itemNome);
                        sendToSingleClient(vendedor, "Voce vendeu " + itemNome + " para o ganhador " + compradorVencendoLance);
                    }
                    
                    else {
                        broadcastToAllClients("O produto "+itemNome+" nao foi vendido e foi finalizado");
                        sendToSingleClient(vendedor, "produto nao vendido: " + itemNome);
                    }
                }
                
                itensExpirados.clear();
                
                long millisecondsPerSecond = 1000;
                try {
                    sleep(1 * millisecondsPerSecond);
                } catch (InterruptedException e) { }
            }
        }
    };
    
    static {
        checarItensExpirados.start();
    }
    
    /*
    ** METODO PARA ENVIAR PARA UM UNICO CLIENTE
    */
    private static void sendToSingleClient(String clientId, String s) {
        String message = "";
        
        message += Protocol.PROTOCOL_NAME_AND_VERSION + "\r\n";
        message += "ALERTA " + s + "\r\n";
        message += "OBRIGADO\r\n";
                        
        byte[] buffer = new byte[512], messageBytes = message.getBytes();

        for (int i = 0; i < buffer.length; i++){
            buffer[i] = '\0';
        }

        int tamanho = Math.min(messageBytes.length, buffer.length -  1);
        
        for (int i = 0; i < tamanho; i++) {
            buffer[i] = messageBytes[i];
        }
        
        log("Enviando mensagem para : " + clientId);        
        DatagramPacket packet;
        
        InetSocketAddress portAndAddress = clientesRegistradosBroadcast.get(clientId);
        
        if (portAndAddress == null) {
            log("Mensagem nao enviada: sem endereco para o cliente " + clientId);
            return;
        }
    
        InetAddress address = portAndAddress.getAddress();
        int port = portAndAddress.getPort();
        packet = new DatagramPacket(buffer, buffer.length, address, port);
        
        DatagramSocket socket = null;
        
        try {
            socket = new DatagramSocket();
            socket.send(packet);
        } catch (Exception e) { log("Error: " + e.getMessage());
        } finally {
            if (socket != null) { socket.close();}
        }
    }
    
    /*
    ** METODO PARA ENVIAR PARA TODOS OS CLIENTES REGISTRADOS
    */
    private static void broadcastToAllClients(String s) {
        String message = "";
        
        message += Protocol.PROTOCOL_NAME_AND_VERSION + "\r\n";
        message += "ALERTA " + s + "\r\n";
        message += "OBRIGADO\r\n";
 
        byte[] buffer = new byte[512], messageBytes = message.getBytes();

        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = '\0';
        }
        
        int tamanho = Math.min(messageBytes.length, buffer.length -  1);
        
        for (int i = 0; i < tamanho; i++) {
            buffer[i] = messageBytes[i];
        }
        
        log("Enviando mensagem para : " + clientesRegistradosBroadcast);
        List<DatagramPacket> packetQueue = new ArrayList<DatagramPacket>();
        
        synchronized(clientesRegistradosBroadcast) {
            for (InetSocketAddress portAndAddress : clientesRegistradosBroadcast.values()) {
                InetAddress address = portAndAddress.getAddress();
                int port = portAndAddress.getPort();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
                packetQueue.add(packet);
            }
        }
        
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            
            for (DatagramPacket packet : packetQueue) {
                socket.send(packet);
            }
                    
        } catch (Exception e) { log("Error: " + e.getMessage());
        } finally {
            if (socket != null) { socket.close(); }
        }
    }
    
    /*
    ** COMANDOS DE RESPOSTA DO LEILOEIRO
    ** TENTATIVA DE CRIAR AS RESPOSTAS PARA OS CIENTES
    */
    private void commandLoop() {
        System.out.println("Esperando os comando dos clientes.");
        String line;
        
        Scanner in = new Scanner(getSocketIn());
 
        while (in.hasNextLine()) {
            line = in.nextLine();
            log(getClientId() + " <<< " + line);
            Scanner lineScanner = new Scanner(line.trim());
            
            if (lineScanner.hasNext()){
                String comando = lineScanner.next();
                
                List<String> arguments = new ArrayList<String>();
                while (lineScanner.hasNext()) {
                    arguments.add(lineScanner.next());
                }
                
                Map<String,String> labelValuePairs;
                
                if (comando.equals("OBRIGADO")) {
                    continue;
                }
                else {
                    try {labelValuePairs = lerDoClient(in); } catch (Exception e) {
                        log("Entrada errada: " + e.getMessage());
                        respond("ERROR Entrada errada.");
                        continue;
                    }
                }

                if (comando.equals("TCHAU")){
                    return;
                }
                
                else if (comando.equals("BROWSE") && arguments.isEmpty()){
                    System.out.println("Enviando todos os leiloes.");
                    String response = "";
                    
                    synchronized(itemsVenda) {
                        for (Item item : itemsVenda.values()){
                            System.out.println("Item: "+item);
                            response += "PRODUTO\r\n";
                            response += item.toLabelValuePairsString().replaceAll("$", "\r").trim() + "\r\n";
                            response += "FIMPRODUTO\r\n";
                        }
                    }
                    respond(response.isEmpty() ? "OK Sem Itens" : response);
                }
                
                else if (comando.equals("BROWSE")) {
                    System.out.println("Enviando leilao especifico");                    
                    String response = "";
                    
                    synchronized(itemsVenda) {
                        for (String itemId : arguments) {
                            if (itemsVenda.containsKey(itemId)) {
                                Item item = itemsVenda.get(itemId);
                                response += "PRODUTO\r\n";                        
                                response += item.toLabelValuePairsString().replaceAll("$", "\r");
                                response += "PRODUTO\r\n";                        
                            }
                        }
                    }
                    respond(response);
                }
                
                else if (comando.equals("VENDER")){
                    Item item;
                    try {
                        item = new Item(labelValuePairs); } catch (Exception e) {
                        respond("ERROR " + e.getMessage());
                        continue;
                    }
                    
                    String itemId = java.util.UUID.randomUUID().toString();
                    
                    item.setId(itemId);
                    item.setVendedorId(getClientId());
                    
                    synchronized(itemsVenda) {
                        itemsVenda.put(itemId, item);
                    }
                    
                    respond("ID " + itemId);
                }
                else if (comando.equals("LANCE")) {
                    String itemId = null;
                    java.math.BigDecimal amount = null;
                    
                    try {
                        itemId = labelValuePairs.get("ID");
                        amount = new java.math.BigDecimal(labelValuePairs.get("PRECO"));
                        lance(itemId, amount, getClientId());
                    }
                    catch (Exception e) {
                        respond("ERROR " + e.getMessage());
                        continue;
                    }
                    
                    respond("Feito. Voce tem o lance mais alto.");
                }
                
                else if (comando.equals("CANCELAR")) {
                    if (!labelValuePairs.containsKey("ID")) {
                        respond("ERROR Requisicao Errada: Falta o ID");
                        continue;
                    }
                    
                    String itemId = labelValuePairs.get("ID");
                    Item item;
                    
                    synchronized(itemsVenda) {
                        
                        if (!itemsVenda.containsKey(itemId)){
                            String possivelNomeItem = itemId;
                            boolean itemEncontrado = false;
                            
                            for (Item tempItem : itemsVenda.values()){
                                if (tempItem.getNome() != null && tempItem.getNome().trim().toLowerCase().matches(possivelNomeItem.trim().toLowerCase())){
                                    itemEncontrado = true;
                                    itemId = tempItem.getId();
                                    break;
                                }
                            }
                            
                            if (!itemEncontrado || !itemsVenda.containsKey(itemId)) {
                                respond("ERROR Sem item");
                                continue;
                            }
                        }

                        item = itemsVenda.get(itemId);
                        
                        if (!item.getVendedorId().equals(getClientId())){
                            respond("ERROR Voce nao e o vendedor");
                            continue;
                        }
                        else if (item.finalizado()) {
                            respond("ERROR Nao e possivel remover item expirado");
                        }
                    }
                        
                    String token = java.util.UUID.randomUUID().toString();
                    
                    final String itemIdCopia = new String(itemId);
                    acoesAguardoConfirmacao.put(token, new Thread(){
                        public void run(){
                            try {
                                cancelar(itemIdCopia);
                            } catch (Exception e) {
                                this.stop(e);
                            }
                        }
                    });
                    
                    assert(item != null);
                    respond(item.toLabelValuePairsString() + "\r\nTOKEN " + token);
                }
                else if (comando.equals("CONFIRMAR")) {
                    if (arguments.size() == 0) {
                        respond("ERROR Requisicao errada: faltando o token");
                    }
                    else if (arguments.size() > 1){
                        respond("ERROR Requisicao errada: contem mais de um token.");
                    }
                    
                    Runnable acao;
                    String token = arguments.get(0);
                    
                    synchronized(acoesAguardoConfirmacao) {
                        if (!acoesAguardoConfirmacao.containsKey(token)) {
                            respond("ERROR Sem token de confirmacao.");
                        }
                        
                        acao = acoesAguardoConfirmacao.remove(token);
                    }
                    try { acao.run(); }
                    catch (Exception e) {
                        respond("ERROR " + e.getMessage());
                        continue;
                    }
                    respond("OK Sucesso.");
                }
                else if (comando.equals("PING")) {
                    String response = "PONG";
                    
                    for (String argument : arguments) {
                        response += " " + argument;
                    }
                    respond(response);
                }
                else {
                    respond("ERROR Comando desconhecido: " + comando);
                }
            }
        }
    }

    private void lance(String itemId, java.math.BigDecimal valor, String clientId) throws Exception {
        synchronized(itemsVenda) {
            if (itemsVenda.containsKey(itemId)) { }
            else {
                String possivelNomeItem = itemId;
                boolean itemEncontrado = false;
                
                for (Item item : itemsVenda.values()) {
                    if (item.getNome() != null && item.getNome().trim().toLowerCase().matches(possivelNomeItem.trim().toLowerCase())){
                        itemEncontrado = true;
                        itemId = item.getId();
                        break;
                    }
                }
                
                if (!itemEncontrado) {
                    throw new Exception("Item nao esta a venda: " + itemId);
                }
            }
            
            Item item = itemsVenda.get(itemId);
            
            if (item.getPreco().compareTo(valor) >= 0) {
                throw new Exception("Lance " + valor + " menor ou igual ao preco atual " + item.getPreco());
            }
            else if(item.finalizado()){
                throw new Exception("Item expirado");
            }
            else {
                item.setPreco(valor);
                item.setCompradorVencendoLanceId(clientId);
                itemsVenda.put(itemId, item);
            }
        }
    }
    
    private void cancelar(String itemId) throws Exception {
        synchronized(itemsVenda) {
            if (!itemsVenda.containsKey(itemId)) {
                throw new Exception("Sem o item para venda.");
            }
            else {
                Item item = itemsVenda.get(itemId);
                if (item.finalizado()) {
                    throw new Exception("Item expirado nao sera removido");
                }
                else {
                    itemsVenda.remove(itemId);
                }
            }
        }
    }
    
    private Map<String,String> lerDoClient() throws Exception {
        String response = lerMensagem();
        return parseResponse(response);
    }

    private void respond(String message) {
        message = message.trim() + "\r\nOBRIGADO\r";
        log(message.replaceAll("^|(\n)(.)", "$1" + /* XXX sanitize */ getClientId() + " >>> $2"));
        getSocketOut().println(message);
    }

    protected String lerMensagem() {
        String message = lerMensagemInteira(getSocketIn());
        log(message.replaceAll("^|(\n)(.)", "$1" + getClientId() + " <<< $2"));
        return message;
    }

    /*
    ** INICIO TRATAMENTO DOS SCANNERS EM MULTI
    */    
    
    private Map<String,String> lerDoClient(Scanner reusarScanner) throws Exception {
        String request = lerMensagem(reusarScanner);
        log(request.replaceAll("^|(\n)(.)", "$1" + getClientId() + " <<< $2"));
        return parseResponse(request);
    }
    
    private String lerMensagem(Scanner reuseScanner){
        String message = "";
        String line;
        
        try {
            Scanner sc = reuseScanner;
            
            while(sc.hasNextLine()){
                line = sc.nextLine().replaceAll("[\r\n]*$", "") + "\n";
                message += line;
                if (line.trim().equals("OBRIGADO")){
                    //System.out.println("FIM DAS MENSAGENS");
                    break;
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
                
        return message;
    }
    
    /* ******************* FIM DO TRATAMENTO ***************** */
   
    public static void runLeiloeiroTCP(int tcpPortNumber) throws IOException {
        log("Escutando porta " + tcpPortNumber + "...");
        new Leiloeiro(tcpPortNumber);
    }
    
    public static void main(String[] args) throws IOException {
        debug = DEFAULT_DEBUG_VALUE;
                
        int tcpPortNumber = Protocol.DEFAULT_PORT_NUMBER;
        try {
            int number = new Integer(args[0]);
            if (number >= 1 && number <= 65535) { tcpPortNumber = number; }
                    
        } catch (Exception e) {}

        runLeiloeiroTCP(tcpPortNumber);
    }
}