package com.idiots.prophunt;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;

@Slf4j
@Singleton
public class BetterPropHuntDataManager {
    private final String baseUrl = "http://73.117.225.50:8080";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject
    private BetterPropHuntPlugin plugin;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    protected void updatePropHuntApi(BetterPropHuntPlayerData data)
    {
        String username = urlifyString(data.username);
        String url = baseUrl.concat("/prop-hunters/"+username);

        try
        {
            Request r = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSON, gson.toJson(data)))
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.debug("Error sending post data", e);
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Successfully sent prop hunt data");
                        response.close();
                    }
                    else
                    {
                        log.debug("Post request unsuccessful");
                        response.close();
                    }
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    public void getPropHuntersByUsernames(String[] players) {
        String playersString = urlifyString(String.join(",", players));

        try {
            Request r = new Request.Builder()
                    .url(baseUrl.concat("/prop-hunters/".concat(playersString)))
                    .get()
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.info("Error getting prop hunt data by username", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if(response.isSuccessful()) {
                        try
                        {
                            JsonArray j = gson.fromJson(response.body().string(), JsonArray.class);
                            HashMap<String, BetterPropHuntPlayerData> playerData = parsePropHuntData(j);
                            plugin.updatePlayerData(playerData);
                        }
                        catch (IOException | JsonSyntaxException e)
                        {
                            log.error(e.getMessage());
                        }
                    }

                    response.close();
                }
            });
        }
        catch(IllegalArgumentException e) {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    private HashMap<String, BetterPropHuntPlayerData> parsePropHuntData(JsonArray j) {
        HashMap<String, BetterPropHuntPlayerData> l = new HashMap<>();
        for (JsonElement jsonElement : j)
        {
            JsonObject jObj = jsonElement.getAsJsonObject();
            String username = jObj.get("username").getAsString();
            BetterPropHuntPlayerData d = new BetterPropHuntPlayerData(jObj.get("username").getAsString(),
                    jObj.get("hiding").getAsBoolean(), jObj.get("modelID").getAsInt());
            l.put(username, d);
        }
        return l;
    }

    private String urlifyString(String str) {
        return str.trim().replaceAll("\\s", "%20");
    }
}
