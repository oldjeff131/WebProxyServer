import java.net.*;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import java.io.*;

//簡單的 HTTP 代理伺服器實作
public class ServerBrowser {
    // 預設代理伺服器監聽的埠號
    private static int port = 8080; 
    private static ServerSocket serverSocket;
    //簡單的快取實作，將請求的結果存放於此
    private static Map<String, byte[]> cache = new HashMap<>();

    //初始化代理伺服器
    public static void init(int p) 
    {
        port=p;
        try 
        {
            // 創建監聽用的 ServerSocket
            serverSocket = new ServerSocket(port);
            System.out.println("Proxy started on port: " + port); 
        }
        catch (IOException e) 
        {
            System.err.println("無法啟動代理伺服器: " + e.getMessage());
            System.exit(-1);
        }
    }

    //處理客戶端請求
    public static void handle(Socket client) 
    {
        Socket server = null;
        HttpRequest request = null;
        HttpResponse response = null;
        try 
        {
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
            request = new HttpRequest(fromClient);
        }
        catch (IOException e) 
        {
            System.out.println("Error reading request from client: " + e);
            return;
        }
        try
        {
            
            String cacheKey = request.getURI();
            if (cache.containsKey(cacheKey)) 
            {
                System.out.println("從快取中讀取請求：" + cacheKey);
                OutputStream toClient = client.getOutputStream();
                //快取中的內容返回給客戶端
                toClient.write(cache.get(cacheKey)); 
                toClient.flush();
                client.close();
                return;
            }
        }
        catch(IOException e)
        {
            System.out.println("Error: " + e);
        }
        //將請求發送至伺服器
        try 
        {
            if (request.getPort() == 443) {
                //建立HTTPS，使用SSLSocket
                SSLSocketFactory factory =(SSLSocketFactory) SSLSocketFactory.getDefault();
                server = factory.createSocket(request.getHost(), request.getPort());
                System.out.println("使用 SSLSocket 連接到 HTTPS 伺服器: " + request.getHost());
            }
            else
            {
                //使用普通的Socket連接到HTTP伺服器
                server = new Socket(request.getHost(), request.getPort());
                System.out.println("使用普通 Socket 連接到 HTTP 伺服器: " + request.getHost());
            }
            //使用 DataOutputStream 發送請求給伺服器
            DataOutputStream toServer = new DataOutputStream(server.getOutputStream());
            //寫入HTTP請求
            toServer.writeBytes(request.toString());
            toServer.flush();
        }
        catch (UnknownHostException e) 
        {
            System.out.println("Unknown host: " + request.getHost());
            System.out.println(e);
            return;
        }
        catch (IOException e) 
        {
            System.out.println("Error writing request to server: " + e);
            return;
        }
        /* Read response and forward it to client */
        try 
        {
            //使用 DataInputStream 讀取伺服器的回應
            DataInputStream fromServer = new DataInputStream(server.getInputStream());
            //解析伺服器的回應並創建 HttpResponse 對象
            response = new HttpResponse(fromServer);
            //使用DataOutputStream將回應轉發給客戶端
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            //將回應標頭和主體寫入客戶端
            toClient.writeBytes(response.toString());
            toClient.write(response.body);
            /* Write response to client. First headers, then body */
            client.close();
            server.close();
        }
            //將物件插入快取
            //填寫(僅限選擇性練習)
        catch (IOException e) 
        {
            System.out.println("Error writing response to client: " + e);
        }
    }

    public static void main(String[] args) 
    {
        //檢查命令行參數以指定埠號
        int myPort = 0;
        
        //檢查命令行參數以指定埠號
        if (args.length > 0) 
        {
            try 
            {
                myPort = Integer.parseInt(args[0]);
            } 
            catch (ArrayIndexOutOfBoundsException e) 
            {
                System.out.println("Need port number as argument");
                System.exit(-1);
            } 
            catch (NumberFormatException e) 
            {
                System.out.println("Please give port number as integer.");
                System.exit(-1);
            }
        }
        
        init(port);

        while (true) 
        {
            try 
            {
                Socket client = serverSocket.accept();
                new Thread(() -> handle(client)).start();
            } 
            catch (IOException e) 
            {
                System.out.println("Error reading request from client: " + e);
                //無法繼續處理該請求，因此跳到 while 迴圈的下一次迭代
                continue;
            }
        }
    }
}
