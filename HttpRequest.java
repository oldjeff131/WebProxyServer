import java.io.*;
import java.net.*;
import java.util.*;

public class HttpRequest {
    //常數變數，表示換行符號
    final static String CRLF = "\r\n";
    final static int HTTP_PORT = 80;
    //儲存請求的參數
    String method;
    String URI;
    String version;
    String headers = "";
    //伺服器主機與端口號
    private String host;
    private int port;

    //透過從客戶端Socket讀取來建立HttpRequest物件，from客戶端輸入流，用於讀取請求資料
    public HttpRequest(BufferedReader from) 
    {
        String firstLine = "";
        try 
        {
            firstLine = from.readLine();
        } 
        catch (IOException e) 
        {
            System.out.println("Error reading request line: " + e);
        }
 
        String[] tmp = firstLine.split(" ");
        //請求方法GET
        method = tmp[0]; 
        //請求的URI:index.html
        URI = tmp[1];
        //HTTP版本HTTP/1.1
        version = tmp[2]; 
 
        System.out.println("URI is: " + URI);
 
        if (!method.equals("GET")) 
        {
            System.out.println("Error: Method not GET");
        }
        try 
        {
            String line = from.readLine();
            while (line.length() != 0) 
            {
                headers += line + CRLF;
                // 需要尋找 Host 標頭，以確定要聯絡的伺服器，特別是當請求 URI 不完整時。
                if (line.startsWith("Host:")) 
                {
                    tmp = line.split(" ");
                    //如果Host標頭包含端口號，分開主機和端口號
                    if (tmp[1].indexOf(':') > 0)
                    {
                        String[] tmp2 = tmp[1].split(":");
                        host = tmp2[0];
                        port = Integer.parseInt(tmp2[1]);
                    } 
                    else
                    {
                        host = tmp[1];
                        port = HTTP_PORT;
                    }
                }
                line = from.readLine();
            }
        } 
        catch (IOException e) 
        {
            System.out.println("Error reading from socket: " + e);
            return;
        }
        System.out.println("Host to contact is: " + host + " at port " + port);
    }

    //返回此請求目標的主機
    public String getHost() 
    {
        return host;
    }

    //返回伺服器的端口號
    public int getPort() 
    {
        return port;
    }

    //將請求轉換為字串，以便重新發送.
    public String toString() 
    {
        String req = "";

        req = method + " " + URI + " " + version + CRLF;
        req += headers;
        //於此代理伺服器不支援持續連線，強制設定連線關閉
        req += "Connection: close" + CRLF;
        req += CRLF;

        return req;
    }
}
