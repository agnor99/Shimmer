package com.lowdragmc.shimmer.client;

import com.lowdragmc.shimmer.Configuration;
import com.lowdragmc.shimmer.client.light.LightManager;
import com.lowdragmc.shimmer.client.model.ShimmerMetadataSection;
import com.lowdragmc.shimmer.client.postprocessing.PostProcessing;
import com.lowdragmc.shimmer.client.shader.ReloadShaderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * @author HypherionSA
 * @date 2022/06/09
 */
public class ShimmerModClient implements ClientModInitializer, SimpleSynchronousResourceReloadListener {

    @Override
    public void onInitializeClient() {
        LightManager.injectShaders();
        PostProcessing.injectShaders();

        /*if (((Object)(MultiLayerModel.Loader.INSTANCE)) instanceof IMultiLayerModelLoader) {
            ((IMultiLayerModelLoader)(Object)(MultiLayerModel.Loader.INSTANCE)).update();
        }*/

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("shimmer")
                    .then(literal("reload_postprocessing")
                            .executes(context -> {
                                for (PostProcessing post : PostProcessing.values()) {
                                    post.onResourceManagerReload(null);
                                }
                                return 1;
                            }))
                    .then(literal("clear_lights")
                            .executes(context -> {
                                LightManager.clear();
                                return 1;
                            }))
                    .then(literal("reload_shader")
                            .executes(context -> {
                                ReloadShaderManager.reloadShader();
                                return 1;
                            }))
                    .then(literal("confirm_clear_resource")
                            .executes(context -> {
                                ReloadShaderManager.cleanResource();
                                return 1;
                            }))
            );
        });

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this);
        //onResourceManagerReload(null);
    }

    @Override
    public ResourceLocation getFabricId() {
        return null;
    }

    @Override
    public void onResourceManagerReload(@Nullable ResourceManager resourceManager) {
        Configuration.load(resourceManager);
        LightManager.INSTANCE.loadConfig();
        PostProcessing.loadConfig();
        ShimmerMetadataSection.onResourceManagerReload();
        LightManager.onResourceManagerReload();
        for (PostProcessing postProcessing : PostProcessing.values()) {
            postProcessing.onResourceManagerReload(resourceManager);
        }
    }
}
