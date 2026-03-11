package com.echoscrobbler;

import io.github.cdimascio.dotenv.Dotenv;
import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.Scanner;

public class SessionGenerator {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("LASTFM_API_KEY");
        String secret = dotenv.get("LASTFM_SHARED_SECRET");

        if (apiKey == null || secret == null) {
            System.err.println("Error: API Key or Shared Secret missing in .env file.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        // 1. Request authentication token
        System.out.println("Step 1: Requesting auth token from Last.fm...");
        HttpRequest reqToken = HttpRequest.newBuilder()
            .uri(URI.create("http://ws.audioscrobbler.com/2.0/?method=auth.gettoken&api_key=" + apiKey + "&format=json"))
            .build();
        HttpResponse<String> resToken = client.send(reqToken, HttpResponse.BodyHandlers.ofString());
        
        String token = resToken.body().split("\"token\":\"")[1].split("\"")[0];

        // 2. User authorization via browser
        System.out.println("Step 2: Opening browser for authorization...");
        Desktop.getDesktop().browse(new URI("http://www.last.fm/api/auth/?api_key=" + apiKey + "&token=" + token));

        System.out.println("\n-------------------------------------------------------");
        System.out.println("Action Required: Authorize the application in your browser.");
        System.out.println("After clicking 'YES, ALLOW', return here and press ENTER.");
        System.out.println("-------------------------------------------------------\n");
        
        new Scanner(System.in).nextLine(); 

        // 3. Generate MD5 signature
        System.out.println("Step 3: Generating API signature...");
        String stringToSign = "api_key" + apiKey + "methodauth.getsession" + "token" + token + secret;
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(stringToSign.getBytes("UTF-8"));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String apiSig = hexString.toString();

        // 4. Retrieve Session Key
        System.out.println("Step 4: Fetching Session Key...");
        HttpRequest reqSession = HttpRequest.newBuilder()
            .uri(URI.create("http://ws.audioscrobbler.com/2.0/?method=auth.getsession&api_key=" + apiKey + "&token=" + token + "&api_sig=" + apiSig + "&format=json"))
            .build();
        HttpResponse<String> resSession = client.send(reqSession, HttpResponse.BodyHandlers.ofString());

        System.out.println("\nLast.fm API Response:");
        System.out.println(resSession.body());
        System.out.println("\nInstructions:");
        System.out.println("- Find the \"key\" value in the JSON response above.");
        System.out.println("- Copy and paste it into your .env file as LASTFM_SESSION_KEY.");
    }
}