package titular.modid.client.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import titular.modid.format.DisplayAdapters;

/** Applies the shared formatter at the vanilla render-to-label call site. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyArgs(method = "render", at = @At(value = "INVOKE", target =
		"Lnet/minecraft/client/render/entity/EntityRenderer;renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
	private void titular$formatLabel(Args args) {
		Entity entity = (Entity) args.get(0);
		Text text = (Text) args.get(1);
		if (entity != null && text != null) {
			args.set(1, DisplayAdapters.formatHeadName(text, entity.getUuid()));
		}
	}
}
