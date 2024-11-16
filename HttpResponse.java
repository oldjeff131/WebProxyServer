import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpResponse {
    //定義回車換行符號
    final static String CRLF = "\r\n";
    //用於讀取物件的緩衝區大小
    final static int BUF_SIZE = 8192;
    //此代理可處理的物件最大大小。目前設定為 100 KB，您可以根據需要調整此大小
    final static int MAX_OBJECT_SIZE = 100000;
    //回應的狀態和標頭
    String version;
    int status;
    String statusLine = "";
    String headers = "";
    String method;
    //回應的主體
    byte[] body = new byte[MAX_OBJECT_SIZE];

    /** Read response from server. */
    public HttpResponse(DataInputStream fromServer) 
    {
        
        /* Length of the object */
        int length = -1;
        boolean gotStatusLine = false;

        /* First read status line and response headers */
    try 
        {
            // 讀取狀態行
            String line = fromServer.readLine();

            while (line != null && !line.isEmpty()) 
            {
                if (!gotStatusLine) 
                {
                    statusLine = line;
                    gotStatusLine = true;
                }
                else 
                {
                    headers += line + CRLF;
                    if (line.toLowerCase().startsWith("content-length")) {
                        String[] parts = line.split(":");
                        length = Integer.parseInt(parts[1].trim());
                    }
                }

                /*
                 * 獲取由 Content-Length 標頭所指示的內容長度。
                 * 不幸的是，並非每個回應都有此標頭。一些伺服器返回 "Content-Length" 標頭，
                 * 而另一些則返回 "Content-length"。在此處需要檢查兩者。
                 */
                // 檢查回應是否包含Content-Length標頭，用於獲取內容的長度
                line = fromServer.readLine();
            }
        } 
        catch (IOException e) 
        {
            System.out.println("Error reading headers from server: " + e);
            return;
        }

        try 
        {
            int bytesRead = 0;
            byte buf[] = new byte[BUF_SIZE];
            boolean loop = false;


            //如果未獲得 Content-Length 標頭，只需迴圈直到連接關閉。
            if (length == -1) 
            {
                loop = true;
            }

            /*
             * 以 BUF_SIZE 的大小分塊讀取主體，並將分塊複製到 body 中。
             * 通常，回應會以比 BUF_SIZE 更小的塊返回。
             * 當我們讀取了 Content-Length 字節或連接關閉時 (沒有 Content-Length 的情況下) 結束迴圈。
             */
            while (bytesRead < length || loop) 
            {
                //以二進位數據方式讀取
                int res = fromServer.read(buf);
                if (res == -1) 
                {
                    break;
                }

                //將讀取的數據複製到body，確保不超過MAX_OBJECT_SIZE
                for (int i = 0; i < res && (i + bytesRead) < MAX_OBJECT_SIZE; i++) 
                {                    
                    body[bytesRead + i] = buf[i];
                }

                bytesRead += res;
            }

        }
         catch (IOException e) 
        {
            System.out.println("Error reading response body: " + e);
            return;
        }
    }

    //將回應轉換為字串以便於重新發送。僅轉換回應標頭，主體未轉換為字串。
    public String toString() {
        String res = "";
        res = statusLine + CRLF;
        res += headers;
        res += CRLF;
        return res;
    }
}