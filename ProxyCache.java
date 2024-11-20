import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.net.ssl.*;


public class ProxyCache {
    //代理伺服器的埠號 
    private static int port;
    //客戶端連線的伺服器Socket
    private static ServerSocket socket;
    //使用ConcurrentHashMap作為快取容器，儲存已經請求過的資料
    private static final Map<String, CachedObject> cache = new ConcurrentHashMap<>();
    //簡單的快取實作，將請求的結果存放於此
    public static void init(int p) 
    {
        port = p;
        try 
        {
            //建立監聽指定端口的ServerSocket
            socket = new ServerSocket(port);
        }
        catch (IOException e) 
        {
            System.out.println("Error creating socket: " + e);
            System.exit(-1);
        }
    }

    public static void handle(Socket client) 
    {
        ProxyCache proxy = new ProxyCache();
        Socket[] server =new Socket[2] ;// 用數組包裝 server
        HttpRequest request = null;
        HttpResponse response = null;
        boolean httpbool=false;
        boolean httpsbool=false;
        boolean cachebool=false;
        String cacheKey=null;
        CachedObject cachedObject=null;
        /*
         * 處理請求。若出現任何異常，則返回並結束此次請求。
         * 這可能會導致客戶端掛起一段時間，直到超時。
         */

        //讀取請求
        try 
        {
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
            request = new HttpRequest(fromClient);
            // 檢查是否有快取
            cacheKey = request.getHost() + request.getURI();
            cachedObject = cache.get(cacheKey);
            if (cachedObject != null && cachedObject.isValid()) 
            {
                cachebool=true;
            }
            else
            {
                cachebool=false;
            }
        }
        catch (IOException e) 
        {
            System.out.println("Error reading request from client: " + e);
            return;
        }

        if(!cachebool)
        {
            //將請求發送至伺服器
            if ("CONNECT".equalsIgnoreCase(request.getMethod())) 
            {
                try 
                {
                    httpsbool=true;
                    server[0] = new Socket(request.getHost(), request.getPort());
                    System.out.println("使用 CONNECT 方法連接到 HTTPS 伺服器: " + request.getHost() + "，埠號: " + request.getPort());
        
                    // 回應客戶端，表示隧道已建立
                    DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
                    toClient.writeBytes("HTTP/1.1 200 Connection Established\r\n\r\n");
                    toClient.flush();
        
                    // 雙向資料轉發
                    Thread forwardClientToServer = new Thread(() -> proxy.forwardData(client, server[0]));
                    Thread forwardServerToClient = new Thread(() -> proxy.forwardData(server[0], client));
                    forwardClientToServer.start();
                    forwardServerToClient.start();
        
                    // 等待資料轉發執行緒完成
                    forwardClientToServer.join();
                    forwardServerToClient.join();
                }
                catch (Exception e) 
                {
                    System.err.println("Error handling CONNECT method: " + e.getMessage());
                }
                finally 
                {
                    try
                    {
                        if (server[0] != null) server[0].close();
                    } 
                    catch (IOException e) 
                    {
                        System.err.println("Error closing server socket: " + e.getMessage());
                    }

                }
            }
            else if ("GET".equalsIgnoreCase(request.getMethod()))
            {
                try
                {
                    httpbool=true;
                    // 處理普通 HTTP 請求
                    server[1] = new Socket(request.getHost(), request.getPort());
                    System.out.println("使用普通 Socket 連接到 HTTP 伺服器: " + request.getHost());
        
                    // 使用 DataOutputStream 發送請求給伺服器
                    DataOutputStream toServer = new DataOutputStream(server[1].getOutputStream());
        
                    // 寫入 HTTP 請求
                    toServer.writeBytes(request.toString());
                    toServer.flush();
                }
                catch (UnknownHostException e) 
                {
                    System.out.println("無法解析主機: " + e);
                }
                catch (IOException e) 
                {
                    System.out.println("I/O 錯誤: " + e); 
                }
            }

            /* Read response and forward it to client */
            try 
            {
                DataInputStream fromServer=null;
                //使用 DataInputStream 讀取伺服器的回應
                if(httpbool)
                {
                    fromServer = new DataInputStream(server[1].getInputStream());
                }
                else if(httpsbool)
                {
                    fromServer = new DataInputStream(server[0].getInputStream());
                }
            
                //解析伺服器的回應並創建 HttpResponse 對象
                response = new HttpResponse(fromServer);

                // 讀取伺服器的響應並將其發送回客戶端
                byte[] buffer = new byte[8192];
                int bytesRead;
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                while ((bytesRead = fromServer.read(buffer)) != -1) 
                {
                    responseBuffer.write(buffer, 0, bytesRead);
                }

                //使用DataOutputStream將回應轉發給客戶端
                DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
                // 將伺服器的響應寫入客戶端
                toClient.write(responseBuffer.toByteArray());
                toClient.flush();

                //將回應標頭和主體寫入客戶端
                toClient.writeBytes(response.toString());
                toClient.write(response.body);

                // 存入快取
                cache.put(cacheKey, new CachedObject(responseBuffer.toByteArray()));

                /* Write response to client. First headers, then body */
                client.close();

                if(httpbool)
                {
                    server[1].close();
                }
                else if(httpsbool)
                {
                    server[0].close();
                }
            }
                //將物件插入快取
                //填寫(僅限選擇性練習)
            catch (IOException e) 
            {
            System.out.println("Error writing response to client: " + e);
            
            }   
        }
        else
        {
            //如果快取有效，直接將快取資料返回給客戶端
            //System.out.println("Cache hit: " + request.getURI());
            //DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            //toClient.writeBytes(cachedObject.getResponse());
            //toClient.flush();
        }
    }
    
    

        

    public static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) 
            {

            String line;
            String requestURI = "";

            // 讀取 HTTP 請求，並提取 URI
            while ((line = in.readLine()) != null && !line.isEmpty()) 
            {
                if (line.startsWith("POST")) 
                {
                    //提取POST請求的 URI (假設要上傳的檔案的 URI)
                    requestURI = line.split(" ")[1];
                }
            }

            //在此處處理檔案上傳的邏輯

            // 响應客戶端
            out.writeBytes("HTTP/1.1 200 OK\r\n");
            out.writeBytes("Content-Type: text/html\r\n\r\n");
            out.writeBytes("<html><body><h1>Upload Successful!</h1></body></html>");
            
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }

    //讀取命令列參數並啟動代理伺服器
    public static void main(String args[]) 
    {
        int myPort = 0;
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

        init(myPort);

        //主迴圈。監聽傳入的連接，並為每個連接生成一個新執行緒來處理
        //Socket client = null;

        while (true) 
        {
            try 
            {
                //client = socket.accept();
                //handle(client);
                Socket client = socket.accept();
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
    public void forwardData(Socket inputSocket, Socket outputSocket){
        try (InputStream inputStream = inputSocket.getInputStream();
             OutputStream outputStream = outputSocket.getOutputStream()) 
             {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) 
            {
                outputStream.write(buffer, 0, bytesRead);
            }
        } 
        catch (IOException e) 
        {
            System.err.println("Error forwarding data: " + e.getMessage());
        }
    }

    // 定義緩存物件，包含響應資料和過期時間
    static class CachedObject 
    {
        private final byte[] response;
        private final long timestamp;

        public CachedObject(byte[] response) 
        {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        public byte[] getResponse() 
        {
            return response;
        }

        public boolean isValid() 
        {
            // 快取過期時間設為5分鐘（300000毫秒）
            return System.currentTimeMillis() - timestamp < 300000;
        }
    }
    
}