package main;

import java.util.*;
import java.io.*;


public abstract class ClientLeiloeiro {

    protected static boolean debug;
    
    /*
    ** METODO PARA LER MENSAGEM CLIENT <-> SERVIDOR
    */
    protected String lerMensagemInteira(BufferedReader in) {
        String message = "";
        String line;
        
        try {
            while ((line = in.readLine()) != null) {
                message += line.replaceAll("[\r\n]*$", "") + "\n";
                System.out.println("Mensagem: "+message);
                if (line.matches("^OBRIGADO\\b")){
                    break;
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
                
        return message;
    }

    /*
    ** METODO PARA TRATAR E CORRIGIR NOVAS LINHAS
    ** NA COMUNICACAO CLIENT <-> SERVIDOR
    */
    protected void semConterNewlines(String s) throws Exception {
        if (s.matches("[\r\n]")) { throw new Exception("Erro interno do servidor: caracteres invalidos"); }
    }
    
    /*
    ** METODO PARA ANALISAR A MENSAGEM ENVIADA CLIENT <-> SERVIDOR
    */
    protected Map<String,String> parseResponse(String response) {
        Scanner sc = new Scanner(response);
        Map<String,String> map = new HashMap<String,String>();
        
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            Scanner lineScanner = new Scanner(line);
            String label = "", value = "";
            
            if (lineScanner.hasNext()) {
                label = lineScanner.next().trim();
                if (lineScanner.hasNext()){
                    value = lineScanner.nextLine().trim();
                }
                map.put(label, value);
            }
        }
        return map;
    }
   
    /**
     ** TENTATIVA DE CRIAR UM SISTEMA DE LOG NO SERVIDOR
     */
    protected static void log(String s)
    {
        if (debug) System.out.println(s.trim());
    }
}