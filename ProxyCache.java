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
    private static final int HTTP_PORT = 80;
    private static final int CACHE_EXPIRY_TIME = 60000; // 1 minute cache expiry
    private static Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    // 用來表示快取資料
    static class CacheEntry {
        HttpResponse response;
        long timestamp;

        public CacheEntry(HttpResponse response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        // 檢查是否過期
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_EXPIRY_TIME;
        }
    }

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

    public static void handle(Socket client) throws IOException 
    {
        ProxyCache proxy = new ProxyCache();
        Socket[] server =new Socket[2] ;// 用數組包裝 server
        HttpRequest request = null;
        HttpResponse response = null;
        boolean httpbool=false;
        boolean httpsbool=false;
        boolean cachebool=false;
        CacheEntry cacheKey=null;
        CachedObject cachedObject=null;
        HttpURLConnection connection = null;

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
            cacheKey = cache.get(request.getURI());
            if (cachedObject != null && cachedObject.isValid()) 
            {
                response = cacheKey.response;
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
                    cache.put(request.getURI(), new CacheEntry(response)); // 將資料放入快取
                    System.out.println("Cache miss for: " + request.getURI());
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
                catch (UnknownHostException e) 
                {
                    System.out.println("無法解析主機: " + e);
                    System.out.println("轉換到404網頁並顯示Not Found");
                    proxy.sendErrorResponse(client, 404, "Unknown Host");
                }
                catch (Exception e) 
                {
                    System.out.println("Error handling CONNECT method: " + e);
                    System.out.println("轉換到500網頁並顯示Internal Server Error");
                    proxy.sendErrorResponse(client, 500, "Internal Server Error");
                    
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
                    cache.put(request.getURI(), new CacheEntry(response)); // 將資料放入快取
                    System.out.println("Cache miss for: " + request.getURI());
                    // 寫入 HTTP 請求
                    toServer.writeBytes(request.toString());
                    toServer.flush();
                }
                catch (UnknownHostException e) 
                {
                    System.out.println("無法解析主機: " + e);
                    System.out.println("轉換到404網頁並顯示Not Found");
                    proxy.sendErrorResponse(client, 404, "Unknown Host");
                }
                catch (IOException e) 
                {
                    proxy.sendErrorResponse(client, 500, "Internal Server Error");
                    System.out.println("Error handling CONNECT method: " + e);
                    System.out.println("轉換到500網頁並顯示Internal Server Error"); 
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
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            toClient.writeBytes(response.toString());
            toClient.flush();
            client.close();
        }
    }
    
    

    private void sendErrorResponse(Socket client, int statusCode, String message) {
        try (OutputStream os = client.getOutputStream();
             PrintWriter writer = new PrintWriter(os, true)) {
    
            // 根據 HTTP 標準定義狀態碼與對應訊息
            String statusMessage;
            switch (statusCode) {
                case 404:
                    statusMessage = "Not Found";
                    break;
                case 400:
                    statusMessage = "Bad Request";
                    break;
                case 500:
                    statusMessage = "Internal Server Error";
                    break;
                default:
                    statusMessage = "Unknown Error";
                    break;
            }
    
            // 回應 HTTP 錯誤訊息
            writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            writer.println("Content-Type: text/html; charset=UTF-8");
            writer.println();
            writer.println("<html>");
            writer.println("<head><title>Error " + statusCode + "</title></head>");
            writer.println("<body>");
            writer.println("<h1>Error " + statusCode + ": " + statusMessage + "</h1>");
            writer.println("<p>" + message + "</p>");
            writer.println("</body>");
            writer.println("</html>");
            writer.flush();
    
        } catch (IOException e) {
            System.err.println("Error sending error response: " + e.getMessage());
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
                new Thread(() -> {
                    try {
                        handle(client);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }).start();
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

/* try {
            // 建立 URL 對象
            String Url=request.getURI();
            URL url = new URL(Url);
            // 開啟連接
            connection = (HttpURLConnection) url.openConnection();
            // 設置請求方法
            connection.setRequestMethod("GET");
            // 設置連接超時和讀取超時
            connection.setConnectTimeout(20000); // 20秒
            connection.setReadTimeout(20000);    // 20秒
            // 發送請求並獲取回應碼
            int responseCode = connection.getResponseCode();
            // 檢查回應碼
            DataOutputStream os = new DataOutputStream(socket.getOutputStream());
            if (responseCode == 200) 
            {
                System.out.println("URL 存在，狀態碼: 200 OK");
            }
            else if (responseCode == 404) 
            {
                System.out.println("URL 不存在，狀態碼: 404 Not Found");
                sendErrorResponse(client, 404, "Unknown Host");
            }
            else 
            {
                System.out.println("收到其他回應碼: " + responseCode);
            }
        }
        catch (IOException e)
        {
            System.err.println("無法連接到 URL，可能是無效的網址或網路問題: " + e.getMessage());
        } 
        finally 
        {
            try {
                server = new Socket(request.getHost(), request.getPort());
                DataOutputStream toServer = new DataOutputStream(server.getOutputStream());
                toServer.writeBytes(request.toString());
            } catch (UnknownHostException e) {
                sendErrorResponse(client, 404, "Unknown Host");
                return;
            } catch (IOException e) {
                sendErrorResponse(client, 500, "Internal Server Error");
                return;
            }
        } */