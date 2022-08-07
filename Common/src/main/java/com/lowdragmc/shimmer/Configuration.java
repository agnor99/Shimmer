package com.lowdragmc.shimmer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author KilaBash
 * @date 2022/05/05
 * @implNote Configs
 */
public class Configuration {
    private static final ResourceLocation configLocation = new ResourceLocation(ShimmerConstants.MOD_ID,"shimmer.json");
    public static List<JsonObject> config = new ArrayList<>();

    public static void load(ResourceManager resourceManager) {
        config.clear();
        try {
            List<Resource> resources = resourceManager.getResourceStack(configLocation);
            for (var resource : resources){
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    if (jsonElement instanceof JsonObject jsonObject){
                        config.add(jsonObject);
                    }else {
                        ShimmerConstants.LOGGER.info("failed to parse resource:{}", resource.sourcePackId());
                    }
                }
            }
        }catch (IOException ignored){
            ShimmerConstants.LOGGER.info("failed to get config resources");
        }
    }

}
