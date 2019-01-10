package main;

import java.net.*;
import java.util.*;
import java.io.*;
import java.math.BigDecimal;


public class Client extends ClientLeiloeiro {
    //private static final boolean DEFAULT_DEBUG_VALUE = false;
    
    protected PrintWriter socketOut;
    protected BufferedReader socketIn;
    
    private static String clientId;
    
    /*
    ** CONSTRUTOR
    */
    public Client(String host, int tcpPortNumber) throws IOException, Exception {
        Socket ioSocket;
        
        ioSocket = new Socket(host, tcpPortNumber);
        System.out.println("Conexao Estabelecida.");

        socketOut = new PrintWriter(ioSocket.getOutputStream(), true);
        socketIn = new BufferedReader(new InputStreamReader(ioSocket.getInputStream()));
    
        int udpPortNumber;                
        try { udpPortNumber = bindToUdpPort();}
        catch (SocketException e) { throw new Error(e); }
                
        String request = "";
        request += "OLA " + Protocol.PROTOCOL_NAME_AND_VERSION + "\r\n";
        
        if (udpPortNumber >= 1 || udpPortNumber <= 65535) { request += "ID " + clientId + "\r\n"; }
        else { log("Porta UDP fora de alcance: " + udpPortNumber); }
        
        request += "UDP " + udpPortNumber + "\r\n";
        request += "OBRIGADO\r\n";
        
        Map<String,String> response = sendToServer(request);
    
        if (response.containsKey("ERROR")) { throw new Exception("Erro ao conectar no servidor: \"" + response.get("ERROR") + "\"");}
        else { System.out.println("Logado com sucesso!"); }
    }
    
    /*
    ** METODO PRINCIPAL
    ** ALGUMAS INFORMACOES DE CONEXAO
    */
    public static void main(String[] args) throws IOException {
        
        String hostNameOrIPAddress = "localhost";
        int tcpPortNumber = Protocol.DEFAULT_PORT_NUMBER;
        clientId = Integer.toString((int) (Math.random() * Integer.MAX_VALUE));
        
        Client client = null;
        
        System.out.println("Conectando ao " + hostNameOrIPAddress + ":" + tcpPortNumber + "...");
        
        try { client = new Client(hostNameOrIPAddress, tcpPortNumber); }
        catch (Exception e) { System.err.println("Error: " + e.getMessage());}
        
        client.commandLoop();
    }
    
    /*
    ** METODO PARA "SETAR" PORTAS DE CONEXAO
    */
    private int bindToUdpPort() throws SocketException {
        DatagramSocket trySocket = null;
        int portNumber;
        
        for (portNumber = Protocol.DEFAULT_PORT_NUMBER; portNumber <= 65535; portNumber++) {
            try {
                trySocket = new DatagramSocket(portNumber);
                log("Ligado a porta: " + portNumber);
                break;
            }
            catch (SocketException e) { continue; }
        }
        if (trySocket == null) { throw new SocketException("Sem Portas disponiveis"); }
        
        final DatagramSocket socket = trySocket;
        
        Thread listenAndAlertUser = new Thread() {
            public void run() {
                byte[] buffer = new byte[Protocol.UDP_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                for (;;) {
                    try {
                        socket.receive(packet);
                        log("Recebeu um pacote UDP");
                    } catch (Exception e) { log("Erro ao receber o pacote UDP: " + e.getMessage()); }
                    
                    /*String message = new String(packet.getData(), 0, packet.getLength());*/
                }
            }
        };
        
        listenAndAlertUser.start();
        return portNumber;
    }
    
    
    protected String lerMensagemInteira() {
        String message = lerMensagemInteira(socketIn);
        Scanner messageScanner = new Scanner(message);

        while (messageScanner.hasNextLine()) {
            log("<<< " + messageScanner.nextLine());
        }
        return message;
    }    
    
    /*
    ** METODO PARA CRIAR UM MENU INTERATIVO
    */
    private void commandLoop() {
        System.out.println("Digite \"help\" para lista de comandos disponíveis.");                    
        String line;

        Scanner in = new Scanner(System.in);
 
        for (System.out.print("> "); in.hasNextLine(); System.out.print("> ")) {
            line = in.nextLine();
            Scanner lineScanner = new Scanner(line.trim());
            
            if (lineScanner.hasNext()) {
                String comando = lineScanner.next();
                
                List<String> arguments = new ArrayList<String>();
                while (lineScanner.hasNext()) {
                    arguments.add(lineScanner.next());
                }
                
                /*
                ** SAINDO DO PROGRAMA CLIENTE
                */
                if (comando.equals("sair")) {
                    System.out.println("Ate mais e obrigado pelos peixes!");
                    System.exit(0);
                }
                
                /*
                ** LISTANDO TODOS OS PRODUTOS/LEILOES
                */
                else if (comando.equals("listar") && arguments.isEmpty()) {
                    System.out.println("Lista de todos os leiloes");
                    Collection<Item> itemsParaVenda = itemsParaVenda();
                    System.out.println(itemsParaVenda.size() + " leilao"
                            + (itemsParaVenda.size() == 1 ? "" : "s"));
                    
                    for (Item item : itemsParaVenda) { System.out.println(item); }
                            
                }
                
                /*
                ** LISTANDO PRODUTO/LEILAO ESPECIFICO
                ** ATRAVES DO ID
                */
                else if (comando.equals("listar")) {
                    System.out.println("Listando o leilao");
                    
                    for (Item item : itemsParaVenda()) {
                        if (arguments.contains(item.getId())) {
                            System.out.println(item);
                        }
                    }
                }
                
                /*
                ** VENDENDO PRODUTO (ESTAMOS COM PROBLEMAS NESSA PARTE
                */
                else if (comando.equals("vender")) {
                    try { venderItem(Item.itemDoConsole()); }
                    catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
                }
                
                /*
                ** DANDO LANCE
                */
                else if (comando.equals("lance")) {
                    String itemId = null; 
                    BigDecimal quantia = null;
                    
                    try {
                        itemId = arguments.get(0);
                        quantia = new BigDecimal(arguments.get(1));
                        if (arguments.size() > 2) throw new Exception("Muitos argumentos");
                    }
                    catch (Exception e) {
                        System.err.println("Uso: lance <PRODUTO_ID|PRODUTO_NOME> <VALOR>"); 
                        System.err.println("Exemplo: lance tv 19.00");                         
                        continue;
                    }
                    
                    try { lance(new Item(itemId), quantia); }
                    catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
                }
                
                /*
                ** CANCELAR ACAO
                */
                else if (comando.equals("remover")) {
                    if (arguments.isEmpty()) {
                        System.err.println("Uso: cancelar <PRODUTO_ID>[, ...]");
                        continue;
                    }
                    
                    for (String itemId : arguments) {
                        Item item;
                        String token;
                        
                        try {
                            Map<String,Item> tokenAndItemTuple = requestCancelarItem(new Item(itemId));
                            token = (String)(tokenAndItemTuple.keySet().toArray())[0];
                            item = tokenAndItemTuple.get(token);
                            
                            log("Token de cancelamento recebido: " + token);
                        }
                        catch (Exception e) {
                            System.err.println("Error: " + e.getMessage());
                            continue;
                        }
                        
                        System.out.print(item);
                        String perguntaPrompt=  "Remover produto? ([s]im/[N]AO) ";
                        System.out.println(perguntaPrompt);
                        while (in.hasNextLine()) {
                            String resposta = in.nextLine().trim().toLowerCase();
                            if (resposta.matches("sim|s")) {
                                try { confirmarAcao(token); }
                                catch (Exception e) { System.err.println("Error: " + e.getMessage()); }
                                break;
                            }
                            else if (resposta.matches("nao|n")) { break; }
                            else { System.out.print(perguntaPrompt); }
                        }
                    }
                }
                
                /*
                ** LISTA OS COMANDOS DISPONIVEIS
                */
                else if (comando.toLowerCase().matches("ajuda|help|h|\\?")) {
                    System.out.println("Listar [<PRODUTO_ID>[, ...]]            Mostrar produto especifico (ou todos)");
                    System.out.println("Lance <PRODUTO_ID|PRODUTO_NOME> <VALOR> Dar lance em determinado produto");
                    System.out.println("Vender                                  Colocar produto a venda");
                    System.out.println("Remover <PRODUTO_ID|PRODUTO_NOME>       Remover produto da venda");
                    System.out.println("Sair                                    Sair da Aplicacao");
                    System.out.println("ping [MENSAGEM]                         Pingar o servidor");
                }
                else if (comando.equals("ping")) {
                    String request = "PING";
                    
                    for (String argument : arguments) { request += " " + argument; }
                    
                    try { sendToServer(request + "\r\nOBRIGADO\r\n"); }
                    catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
                }
                else {
                    System.out.println("Comando desconhecido: " + line);
                    System.out.println("Digite \"ajuda\" para lista de comandos");                    
                }
            }
        }
    }
    
    /*
    ** METODO PARA ENVIAR A REQUISAO PARA O SERVIDOR
    */
    public Map<String,String> sendToServer(String request) throws Exception {
        log(request.replaceAll("^|(\n)(.)", "$1>>> $2"));
        socketOut.println(request.trim() + "\r");
        String response = lerMensagemInteira();
        return parseResponse(response);
    }
    
    /*
    ** METODO DE CANCELAR UMA VENDA/LANCE
    ** (PRECISA DE AJUSTES)
    */
    private Map<String,Item> requestCancelarItem(Item item) throws Exception {
        String request = "";
        String id = item.getId();
        
        semConterNewlines(id);
        
        request += "CANCELAR\r\n";
        request += "ID " + id + "\r\n";
        request += "OBRIGADO\r\n";
        
        Map<String,String> response = sendToServer(request);
        
        if (response.containsKey("ID") && response.containsKey("TOKEN")) {
            System.out.println("Confirmacao requisitida");
            Map<String,Item> map = new HashMap<String,Item>();
            Item itemRetornado;

            try {
                itemRetornado = new Item(response);
            } catch (IllegalArgumentException e) {
                throw new Exception("Resposta do servidor com problema");
            }
            
            map.put(response.get("TOKEN"), itemRetornado);
            
            return map;
        }
        else if (response.containsKey("ERROR")) {
            throw new Exception("Falha ao requisitar o cancelamento do leilao: " + response.get("ERROR"));
        }
        else{
            throw new Exception("Falha ao requisitar o cancelamento do leilao: " + id);
        }
    }

    /*
    ** CONFIRMACAO DO LANCE CRIADO
    */
    private void confirmarAcao(String token) throws Exception {
        log("Confirmando acao com token " + token + "...");
        
        String request = "";
        
        semConterNewlines(token);
        
        request += "CONFIRMAR " + token + "\r\n";
        request += "OBRIGADO\r\n";
        
        Map<String,String> response = sendToServer(request);
        
        if (response.containsKey("OK")) {
            System.out.println("Lance confirmado");
        }
        else if (response.containsKey("ERROR")) {
            throw new Exception("Falha ao confirmar o lance: " + response.get("ERROR"));
        }
        else {
            throw new Exception("Falha ao confirmar o lance");
        }
    }
    
    /*
    ** DANDO LANCE
    */
    private void lance(Item item, BigDecimal quantia) throws Exception {
        String request = "";
        
        request += "LANCE\r\n";
        request += "ID " + item.getId() + "\r\n";
        request += "PRECO " + quantia + "\r\n";
        request += "OBRIGADO\r\n";
        
        
        Map<String,String> response = sendToServer(request);
        
        if (response.containsKey("OBRIGADO")){
            System.out.println("Lance aceito");
        }
        else if (response.containsKey("ERROR")){
            throw new Exception("Lance nao aceito: " + response.get("ERROR"));
        }
        else {
            throw new Exception("Lance nao aceito");
        }
    }

    /*
    ** METODO DE LISTAR OS PRODUTOS
    */
    private List<Item> itemsParaVenda() {
        List<Item> itemLista = new ArrayList<Item>();

        String request = "BROWSE\r\nOBRIGADO\r";

        log(request.replaceAll("^|(\n)(.)", "$1>>> $2"));                
        socketOut.println(request);
        String response = lerMensagemInteira();
        
        Scanner scanner = new Scanner(response);
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.matches("^PRODUTO\\b")) {
                String itemDescricao = "";
                while(scanner.hasNextLine() && !(line = scanner.nextLine()).matches("^FIMPRODUTO\\b")){
                    itemDescricao += line + "\n";
                }
                                    
                Map<String,String> labelValuePairs = parseResponse(itemDescricao);
                
                if (!labelValuePairs.isEmpty()) {
                    try {
                        itemLista.add(new Item(labelValuePairs));
                    }
                    catch (Exception e) { System.err.println("Descricao do item mal formada: \"" + itemDescricao + "\""); }
                }
            }
        }
        return itemLista;
    }
    
    /*
    ** METODO PRA CRIAR LEILAO
    */
    private void venderItem(Item item) throws Exception {
        String request = "";
        String nome  = item.getNome().replaceAll("[\r\n]", ""),
               preco = item.getPreco().toString(),
               end   = item.getLeilaoTempoFim().toString();
        
        semConterNewlines(nome);
        semConterNewlines(preco);
        semConterNewlines(end);
    
        request += "VENDER\r\n";
        request += "NOME " + nome + "\r\n";
        request += "PRECO " + preco + "\r\n";
        request += "FIM " + end + "\r\n";
        request += "OBRIGADO\r\n";
        
        Map<String,String> response = sendToServer(request);
        
        /*
        ** ERRO AQUI COM A DATA
        */
        
        if (response.containsKey("ID")) {
            System.out.println("Leilao criado com ID: " + response.get("ID"));
        }
        else if (response.containsKey("ERROR")){
            throw new Exception("Erro ao criar o leilao: " + response.get("ERROR"));
        }
        else{
            throw new Exception("Falha ao criar leilao");
        }
    }

}