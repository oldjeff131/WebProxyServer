import java.net.*;
import java.io.*;
import java.util.*;

public class ProxyCache {
    /** Port for the proxy */
    private static int port;
    /** Socket for client connections */
    private static ServerSocket socket;

    /** Create the ProxyCache object and the socket */
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
        Socket server = null;
        HttpRequest request = null;
        HttpResponse response = null;

        /*
         * 處理請求。若出現任何異常，則返回並結束此次請求。
         * 這可能會導致客戶端掛起一段時間，直到超時。
         */

        //讀取請求
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
        //將請求發送至伺服器
        try 
        {
            //開啟與伺服器的 Socket 連接
            server = new Socket(request.getHost(), request.getPort());
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
            // 使用 DataInputStream 讀取伺服器的回應
            DataInputStream fromServer = new DataInputStream(server.getInputStream());
            // 解析伺服器的回應並創建 HttpResponse 對象
            response = new HttpResponse(fromServer);
            // 使用 DataOutputStream 將回應轉發給客戶端
            DataOutputStream toClient = new DataOutputStream(client.getOutputStream());
            // 將回應標頭和主體寫入客戶端
            toClient.writeBytes(response.toString());
            toClient.write(response.body);
            /* Write response to client. First headers, then body */
            client.close();
            server.close();
        }
            //將物件插入快取
            //填寫 (僅限選擇性練習)
        catch (IOException e) 
        {
            System.out.println("Error writing response to client: " + e);
        }
    }

    //讀取命令列參數並啟動代理伺服器
    public static void main(String args[]) 
    {

        int myPort = 8080;
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
        Socket client = null;

        while (true) 
        {
            try 
            {
                client = socket.accept();
                handle(client);
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