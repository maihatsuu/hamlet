package maihatsuu.hamlet.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import maihatsuu.hamlet.villager.HamletVillager;
import maihatsuu.hamlet.villager.VillagerReadiness;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class HamletCommands {
	private HamletCommands() {
	}

	/** Registers {@code /hamlet stock <villager> <item> <count>}, for seeding a villager's stock when testing. */
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> dispatcher.register(
			literal("hamlet")
				.requires(source -> source.hasPermission(2))
				.then(literal("stock")
					.then(argument("villager", EntityArgument.entity())
						.then(argument("item", ItemArgument.item(buildContext))
							.then(argument("count", IntegerArgumentType.integer(1, 64))
								.executes(HamletCommands::addStock)))))
				.then(literal("readiness")
					.then(argument("villager", EntityArgument.entity())
						.executes(HamletCommands::showReadiness)
						.then(literal("end_grace")
							.executes(HamletCommands::endGrace))))
		));
	}

	private static int showReadiness(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		if (!(EntityArgument.getEntity(context, "villager") instanceof Villager villager)) {
			context.getSource().sendFailure(Component.translatable("hamlet.command.not_a_villager"));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.translatable(
			"hamlet.command.readiness",
			VillagerReadiness.inGrace(villager),
			VillagerReadiness.hasSlept(villager),
			VillagerReadiness.hasSocialized(villager),
			VillagerReadiness.hasSpace(villager)
		), false);
		return 1;
	}

	private static int endGrace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		if (!(EntityArgument.getEntity(context, "villager") instanceof Villager villager)) {
			context.getSource().sendFailure(Component.translatable("hamlet.command.not_a_villager"));
			return 0;
		}

		VillagerReadiness.endGrace(villager);
		context.getSource().sendSuccess(() -> Component.translatable("hamlet.command.grace_ended"), false);
		return 1;
	}

	private static int addStock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Entity entity = EntityArgument.getEntity(context, "villager");
		if (!(entity instanceof HamletVillager villager)) {
			context.getSource().sendFailure(Component.translatable("hamlet.command.not_a_villager"));
			return 0;
		}

		int count = IntegerArgumentType.getInteger(context, "count");
		ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(count, false);
		int added = count - villager.hamlet$getStock().addItem(stack).getCount();
		context.getSource().sendSuccess(() -> Component.translatable("hamlet.command.stock_added", added), false);
		return added;
	}
}
