package com.lowdragmc.shimmer.client.shader;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ReloadShaderManager {

    private static Map<ResourceLocation, Optional<Resource>> reloadResources = new HashMap<>();
    public static boolean isReloading = false;
    private static boolean foreReloadAll = false;
    private static final ResourceProvider reloadShaderResource = reloadResources::get;

    private static void recordResource(ResourceLocation resourceLocation, Resource resource) {
        reloadResources.put(resourceLocation, Optional.of(resource));
    }

    public static void cleanResource() {
        message(Component.literal("clear all resource for backup usage"));
        reloadResources.clear();
    }

    private static void recordCopyResource(ResourceLocation resourceLocation, Resource resource) {
        try {
            final byte[] data = resource.open().readAllBytes();

            // FIXME NOT SURE IF THIS IS CORRECT
            Resource copyResource = resource;
            recordResource(resourceLocation, copyResource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void reloadShader() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resourceManager = minecraft.getResourceManager();
        message(Component.literal("start reloading shader"));
        long time = System.currentTimeMillis();
        Map<ResourceLocation, Optional<Resource>> backupResource = reloadResources;
        reloadResources = new HashMap<>();
        isReloading = true;
        foreReloadAll = false;
        try {
            minecraft.gameRenderer.onResourceManagerReload(resourceManager);
            minecraft.levelRenderer.onResourceManagerReload(resourceManager);
            message(Component.literal("reload success"));
            message(Component.literal(MessageFormat.format("cache resource:{0}", reloadResources.size())));
            message(Component.literal(MessageFormat.format("total time cost:{0}s", (System.currentTimeMillis() - time) / 1000f)));
            backupResource.clear();
        } catch (Exception tryException) {
            foreReloadAll = true;
            reloadResources.clear();
            reloadResources = backupResource;
            message(Component.literal("exception occur will reloading , trying to backup").withStyle(ChatFormatting.RED));
            message(Component.literal(MessageFormat.format("error:{0}", tryException.getMessage())).withStyle(ChatFormatting.RED));
            try {
                minecraft.gameRenderer.onResourceManagerReload(resourceManager);
                minecraft.levelRenderer.onResourceManagerReload(resourceManager);
                message(Component.literal("load backup resource successful"));
            } catch (Exception backupException) {
                message(Component.literal("exception occur while trying backup").withStyle(ChatFormatting.RED));
                message(Component.literal(MessageFormat.format("error:{0}", backupException.getMessage())).withStyle(ChatFormatting.RED));
                backupException.addSuppressed(tryException);
                throw backupException;
            }
        } finally {
            isReloading = false;
            foreReloadAll = false;
        }
    }

    private static void message(Component component) {
        Minecraft.getInstance().player.sendSystemMessage(component);
    }

    @Nonnull
    public static ShaderInstance backupNewShaderInstance(ResourceProvider resourceProvider, String shaderName, VertexFormat vertexFormat) throws IOException {
        if (foreReloadAll) {
            return new ShaderInstance(reloadShaderResource, shaderName, vertexFormat);
        }
        ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, shaderName, vertexFormat);
        ResourceLocation shaderResourceLocation = new ResourceLocation(shaderName);
        recordProgramResource(resourceProvider, shaderResourceLocation.getNamespace(), shaderResourceLocation.getPath());
        return shaderInstance;
    }

    public static ShaderInstance backupNewShaderInstance(ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat vertexFormat) throws IOException {
        return backupNewShaderInstance(resourceProvider, shaderLocation.toString(), vertexFormat);
    }

    @Nonnull
    private static void recordProgramResource(ResourceProvider resourceProvider, String nameSpace, String shaderName) throws IOException {
        ResourceLocation programResourceLocation = new ResourceLocation(nameSpace, "shaders/core/" + shaderName + ".json");
        Resource programResource = resourceProvider.getResource(programResourceLocation).get();
        ReloadShaderManager.recordCopyResource(programResourceLocation, programResource);
        JsonObject jsonObject = GsonHelper.parse(new InputStreamReader(resourceProvider.getResource(programResourceLocation).get().open(), StandardCharsets.UTF_8));
        ResourceLocation vertex = new ResourceLocation(GsonHelper.getAsString(jsonObject, "vertex"));
        ResourceLocation vertexResourceLocation = new ResourceLocation(vertex.getNamespace(), "shaders/core/" + vertex.getPath() + ".vsh");
        ReloadShaderManager.recordCopyResource(vertexResourceLocation, resourceProvider.getResource(vertexResourceLocation).get());
        ResourceLocation fragment = new ResourceLocation(GsonHelper.getAsString(jsonObject, "fragment"));
        ResourceLocation fragmentResourceLocation = new ResourceLocation(fragment.getNamespace(), "shaders/core/" + fragment.getPath() + ".fsh");
        ReloadShaderManager.recordCopyResource(fragmentResourceLocation, resourceProvider.getResource(fragmentResourceLocation).get());
    }

    public static ResourceManager reloadResourceManager = new ResourceManager() {
        @Override
        public Set<String> getNamespaces() {
            return null;
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation resourceLocation) {
            return List.of(getResource(resourceLocation).get());
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String s, Predicate<ResourceLocation> predicate) {
            return null;
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String s, Predicate<ResourceLocation> predicate) {
            return null;
        }

        @Override
        public Stream<PackResources> listPacks() {
            return null;
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation resourceLocation) {
            return reloadResourceManager.getResource(resourceLocation);
        }
    };

    public static PostChain backupNewPostChain(TextureManager textureManager, ResourceManager resourceManager, RenderTarget renderTarget, ResourceLocation resourceLocation) throws IOException {
        if (foreReloadAll) {
            return new PostChain(textureManager, reloadResourceManager, renderTarget, resourceLocation);
        }
        PostChain postChain = new PostChain(textureManager, resourceManager, renderTarget, resourceLocation);
        recordPostChainResource(resourceManager, resourceLocation);
        return postChain;
    }

    private static void recordPostChainResource(ResourceManager resourceManager, ResourceLocation resourceLocation) throws IOException {
        Resource postChainResource = resourceManager.getResource(resourceLocation).get();
        recordCopyResource(resourceLocation, postChainResource);
    }

    public static EffectInstance backupNewEffectInstance(ResourceManager resourceProvider, String shaderName) throws IOException {
        if (foreReloadAll) {
            return new EffectInstance(reloadResourceManager, shaderName);
        }
        EffectInstance effectInstance = new EffectInstance(resourceProvider, shaderName);
        recordEffectInstanceResource(resourceProvider, shaderName);
        return effectInstance;
    }

    private static void recordEffectInstanceResource(ResourceManager resourceProvider, String shaderName) throws IOException {
        ResourceLocation resourceLocation = make(ResourceLocation.tryParse(shaderName), (rl) ->
                new ResourceLocation(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".json"));
        Resource effectResource = resourceProvider.getResource(resourceLocation).get();
        recordCopyResource(resourceLocation, effectResource);
        JsonObject effectJsonObject = GsonHelper.parse(new InputStreamReader(resourceProvider.getResource(resourceLocation).get().open(), StandardCharsets.UTF_8));
        String vertex = GsonHelper.getAsString(effectJsonObject, "vertex");
        ResourceLocation vertexResourceLocation = make(ResourceLocation.tryParse(vertex), rl ->
                new ResourceLocation(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".vsh"));
        recordCopyResource(vertexResourceLocation, resourceProvider.getResource(vertexResourceLocation).get());
        String fragment = GsonHelper.getAsString(effectJsonObject, "fragment");
        ResourceLocation fragmentResourceLocation = make(ResourceLocation.tryParse(fragment), rl ->
                new ResourceLocation(rl.getNamespace(), "shaders/program/" + rl.getPath() + ".fsh"));
        recordCopyResource(fragmentResourceLocation, resourceProvider.getResource(fragmentResourceLocation).get());
    }

    private static <T, R> R make(T origin, Function<T, R> transformer) {
        return transformer.apply(origin);
    }
}
