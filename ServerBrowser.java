import java.net.*;
import java.io.*;

//簡單的 HTTP 代理伺服器實作
public class ServerBrowser {
    // 預設代理伺服器監聽的埠號
    private static int port = 8080; 
    private static ServerSocket serverSocket;

    //初始化代理伺服器
    public static void init(int p) 
    {
        port = p;
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
        try 
        {
            //讀取客戶端的 HTTP 請求
            BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
            HttpRequest request = new HttpRequest(fromClient);

            //與目標伺服器建立連線
            Socket server = new Socket(request.getHost(), request.getPort());
            DataOutputStream toServer = new DataOutputStream(server.getOutputStream());
            //傳送請求至目標伺服器
            toServer.writeBytes(request.toString()); 
            toServer.flush();

            //讀取伺服器的回應
            DataInputStream fromServer = new DataInputStream(server.getInputStream());
            HttpResponse response = new HttpResponse(fromServer);

            //回傳伺服器回應給客戶端
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            //回應標頭
            toClient.writeBytes(response.toString()); 
            //回應主體
            toClient.write(response.body); 

            //關閉連線
            client.close();
            server.close();
        }
        catch (IOException e) 
        {
            System.err.println("處理請求時發生錯誤: " + e.getMessage());
        }
    }

    public static void main(String[] args) 
    {
        //檢查命令行參數以指定埠號
        if (args.length > 0) 
        {
            try 
            {
                port = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e) 
            {
                System.err.println("請輸入有效的埠號");
                System.exit(-1);
            }
        }
        // 初始化代理伺服器
        init(port); 

        while (true) 
        {
            try 
            {
                //等待客戶端連線
                Socket client = serverSocket.accept();  
                //啟動新執行緒處理請求
                new Thread(() -> handle(client)).start();
            } 
            catch (IOException e) 
            {
                System.err.println("接受客戶端連線時發生錯誤: " + e.getMessage());
            }
        }
    }
}
