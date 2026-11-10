package maihatsuu.hamlet.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import maihatsuu.hamlet.villager.HamletVillager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

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
				.then(literal("count_ores")
					.then(argument("chunk_radius", IntegerArgumentType.integer(0, 16))
						.executes(HamletCommands::countOres)))
		));
	}

	/** Generates the surrounding chunks if needed, so large radii can stall the server briefly. */
	private static int countOres(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getLevel();
		ChunkPos center = new ChunkPos(BlockPos.containing(context.getSource().getPosition()));
		int radius = IntegerArgumentType.getInteger(context, "chunk_radius");
		int[] counts = new int[2];

		for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
			for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
				for (LevelChunkSection section : level.getChunk(chunkX, chunkZ).getSections()) {
					if (section.hasOnlyAir()) {
						continue;
					}
					for (int x = 0; x < LevelChunkSection.SECTION_WIDTH; x++) {
						for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
							for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
								BlockState state = section.getBlockState(x, y, z);
								if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
									counts[0]++;
								} else if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
									counts[1]++;
								}
							}
						}
					}
				}
			}
		}

		context.getSource().sendSuccess(() -> Component.translatable("hamlet.command.ore_count", counts[0], counts[1]), false);
		return counts[1];
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
