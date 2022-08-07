package com.lowdragmc.shimmer.core.mixins;

import com.lowdragmc.shimmer.ShimmerConstants;
import com.lowdragmc.shimmer.client.shader.ShaderInjection;
import com.lowdragmc.shimmer.platform.Services;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author KilaBash
 * @date 2022/05/02
 * @implNote ProgramMixin, inject custom shader to vanilla shaders
 */
@Mixin(Program.class)
public abstract class ProgramMixin {

    private static final Pattern REGEX_VERSION = Pattern.compile("(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*version(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(\\d+))\\b");

    @Redirect(
            method = "compileShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/shaders/Program;compileShaderInternal(Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;)I"
            )
    )
    private static int injectShaderNameIntoSourceCode(Program.Type type, String shaderName, InputStream inputStream, String pShaderSourceName, GlslPreprocessor glslPreprocessor) throws IOException {
        String string3 = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        if (string3 == null) {
            throw new IOException("Could not load program " + type.getName());
        } else {
            Matcher matcher = REGEX_VERSION.matcher(string3);
            int index = matcher.find() ? matcher.group().length() : 0;
            if (pShaderSourceName.equals("Mod Resources") || pShaderSourceName.equals("Default")){
                string3 = new StringBuilder(string3).insert(index,"\n#line __LINE__ //shaderName:" + shaderName).toString();
            } else {
                string3 = new StringBuilder(string3).insert(index,"\n#line __LINE__ //ShaderSourceName:" + pShaderSourceName).toString();
            }

            int i = GlStateManager.glCreateShader(type.getGlType());
            GlStateManager.glShaderSource(i, glslPreprocessor.process(string3));
            GlStateManager.glCompileShader(i);
            if (GlStateManager.glGetShaderi(i, 35713) == 0) {
                String string4 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
                throw new IOException("Couldn't compile " + type.getName() + " program (" + pShaderSourceName + ", " + shaderName + ") : " + string4);
            } else {
                return i;
            }
        }
    }

    @Inject(method = "compileShaderInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;process(Ljava/lang/String;)Ljava/util/List;"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void injectProcess(Program.Type pType,
                                     String shaderName,
                                     InputStream pShaderData,
                                     String pSourceName,
                                     GlslPreprocessor pPreprocessor,
                                     CallbackInfoReturnable<Integer> cir,
                                     String shader,
                                     int id) {
        if (!Services.PLATFORM.isLoadingStateValid()){
            return;
        }
        boolean isVSH = pType.getName().equals("vertex");
        if (isVSH) {
            if (ShaderInjection.hasInjectVSH(shaderName)) {
                String newShader = ShaderInjection.injectVSH(shaderName, shader);
                List<String> processed = pPreprocessor.process(newShader);
                GlStateManager.glShaderSource(id, processed);
                GlStateManager.glCompileShader(id);
                if (GlStateManager.glGetShaderi(id, 35713) == 0) {
                    String s1 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(id, 32768));
                    ShimmerConstants.LOGGER.error("Couldn't compile " + pType.getName() + " program (" + pSourceName + ", " + shaderName + ") : " + s1);
                } else {
                    cir.setReturnValue(id);
                }
            }
        } else {
            if (ShaderInjection.hasInjectFSH(shaderName)) {
                String newShader = ShaderInjection.injectFSH(shaderName, shader);
                GlStateManager.glShaderSource(id, pPreprocessor.process(newShader));
                GlStateManager.glCompileShader(id);
                if (GlStateManager.glGetShaderi(id, 35713) == 0) {
                    String s1 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(id, 32768));
                    ShimmerConstants.LOGGER.error("Couldn't compile " + pType.getName() + " program (" + pSourceName + ", " + shaderName + ") : " + s1);
                } else {
                    cir.setReturnValue(id);
                }
            }
        }

    }

}
