import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class GetDateTime {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("input the timestamp");
		
		InputStreamReader inp = new InputStreamReader(System.in);
		
		BufferedReader br = new BufferedReader(inp);

	    try {
			String str = br.readLine();
			
			long timeStamp = Long.parseLong(str);
			
			Date date = new Date(timeStamp);
			
			DateFormat dataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
			
			System.out.println(dataFormat.format(date));
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    

	}

}
