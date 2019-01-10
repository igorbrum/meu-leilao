package main;

import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Item
{
    private String id;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    private String nome;
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    private BigDecimal preco;
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    
    private String vendedorId;
    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }
    
    private String compradorVencendoLanceId;
    public String getCompradorVencendoLanceId() { return compradorVencendoLanceId; }
    public void setCompradorVencendoLanceId(String compradorVencendoLanceId) { this.compradorVencendoLanceId = compradorVencendoLanceId; }
    
    private Date leilaoTempoFim;
    public Date getLeilaoTempoFim() { return leilaoTempoFim; }
    
    private boolean finalizado = false;
    public boolean finalizado() { return finalizado; }
    public void setFinalizado(boolean finalizado) { this.finalizado = finalizado; };

    private static AtomicLong uniqueIdContador = new AtomicLong();

    /*
    ** METODO QUE MONTA O MENU DE INPUT DE FORMA INTERATIVA
    */
    public static Item itemDoConsole() {
        Item item = new Item();
        String s = "";
        Scanner consoleScanner = new Scanner(System.in);
        
        try {
            System.out.print("Nome do produto: ");
            s = consoleScanner.nextLine();
            item.nome = s.trim();
            
            for (;;) {
                System.out.print("Preco inicial: ");
                s = consoleScanner.nextLine();
                
                try {
                    item.preco = new BigDecimal(s);
                    if (item.preco.signum() < 0) {
                        System.err.println("Apenas valores positivos.");
                        continue;
                    }
                    else if (item.preco.scale() > 2) {
                         System.err.println("Sem valores fracionados"); //verificar essa parte
                         continue;
                    }
                    item.preco = item.preco.setScale(2);
                    break;
                }
                catch (NumberFormatException e) { continue; }
            }
            
            do {
                System.out.print("Duracao do leilao em segundos: ");
                s = consoleScanner.nextLine();
                s = s.trim();
                //ESSA PARTE É PARA ACEITAR VALOR SEM TER DIGITADO NADA
                //COMO PADRAO VAI SER 10MINUTOS
                if (s.equals("")) {
                    long millisecondsPerMinute = 60 * 1000;
                    //SETADO EM 10MIN COMO PADRAO
                    Date minutosApartirDeAgora = new Date(System.currentTimeMillis() + 10 * millisecondsPerMinute);
                    item.leilaoTempoFim = minutosApartirDeAgora;
                }
                
                //ESSA PARTE PEGA O INPUT DO USUARIO
                else {
                    try {
                        Integer leilaoDuracaoSegundos = Integer.parseInt(s);
                        if (!leilaoDuracaoSegundos.toString().equals(s)) { throw new Exception("Nao e um inteiro."); }
                        else if (leilaoDuracaoSegundos <= 0) { throw new Exception("Duracao precisa ser positiva."); }
                        else {
                            long milissegundosPorSegundo = 1000;
                            item.leilaoTempoFim = new Date(System.currentTimeMillis() + leilaoDuracaoSegundos * milissegundosPorSegundo);
                        }
                    }
                    catch (Exception e) {
                        System.err.println("Problemas ao processar sua entrada: " + e.getMessage());
                        continue;
                    }
                }
            } while (false);
        }
        catch (NoSuchElementException e) { throw new RuntimeException("Fim do Arquivo"); }
        catch (IllegalStateException e) { throw new RuntimeException("Fim do Arquivo"); }
        
        return item;
    }
    
    public Item() {
        id = uniqueItemId();
    }
    
    public Item(String id) {
        this.id = id;
    }

    public Item(Map<String,String> itemDescricao) throws Exception {
        if (itemDescricao.isEmpty()) {
            throw new Exception("Descricao do item vazio");
        }
                
        if (itemDescricao.containsKey("ID")) {
            id = itemDescricao.get("ID");
        }
                
        if (itemDescricao.containsKey("NOME")) {
            nome = itemDescricao.get("NOME");
        }
                
        if (itemDescricao.containsKey("PRECO")) {
            preco = new BigDecimal(itemDescricao.get("PRECO"));
        }
                
        if (itemDescricao.containsKey("FIM")) {
            try {
                String dDate= itemDescricao.get("FIM");
                DateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ssZ yyyy", Locale.ENGLISH);
                Date cDate = df.parse(dDate);
                leilaoTempoFim = cDate;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
                
        if (itemDescricao.containsKey("VENDEDOR")) {
            vendedorId = itemDescricao.get("VENDEDOR");
        }
                
        if (itemDescricao.containsKey("COMPRADOR")) {
            compradorVencendoLanceId = itemDescricao.get("COMPRADOR");
        }
                
        if (itemDescricao.containsKey("FINALIZADO")) {
            finalizado = true;
        }
    }
    
    /*
    ** METODO PARA MONTAR STRING COM OS DADOS DO LEILAO
    */
    public String toLabelValuePairsString() {
        String s = "";
        
        if (id != null) { s += "ID " + id + "\n"; }
                
        if (nome != null) { s += "NOME " + nome + "\n"; }
                
        if (preco != null) { s += "PRECO " + preco + "\n"; }
                
        if (leilaoTempoFim != null) { s += "FIM " + leilaoTempoFim + "\n"; }
                
        if (vendedorId != null) { s += "VENDEDOR " + vendedorId + "\n"; }
                
        if (compradorVencendoLanceId != null) { s += "COMPRADOR " + compradorVencendoLanceId + "\n"; }
                
        if (finalizado) { s += "FINALIZADO" + "\n"; }
        
        return s;
    }
    
    private static String uniqueItemId(){
        return "" + uniqueIdContador.getAndIncrement();
    }

    /*
    ** METODO PARA DAR LANCE
    ** (VERIFICAR USO)
    */
    public boolean darLance(BigDecimal quantia) {
        throw new RuntimeException("Nao implementado");
    }
    
    /*
    ** METODO PRA LISTAR OS ITEMS NO TERMINAL
    */
    public String toString() {
        String s = "";
        
        s += "ID: " + getId() + "\n";
        s += "nome: " + getNome() + "\n";
        s += "preco: " + (getPreco() == null ? "" : "R$ ") + getPreco() + "\n";
        
        if (finalizado()) {
            s += "Expirado: sim\n";
            s += "Leilao finalizado: " + getLeilaoTempoFim() + "\n";
        }
        else {
            s += "Expirado: nao\n";
            s += "Leilao finaliza em : " + getLeilaoTempoFim() + "\n";
        }
        s += "lance que esta vencendo: " + (getCompradorVencendoLanceId() == null ? "sem lance" : getCompradorVencendoLanceId()) + "\n";

        return s;
    }
    
    /*
    ** METODO PRA MONTAR A LISTA DE ITENS
    ** tentativa de criar produtos antes
    */
    public static void main(String[] args) {        
        /*while (true) {
            Item item = itemDoConsole();
            System.out.println(item);
        }*/
    }
}