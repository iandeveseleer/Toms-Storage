package com.tom.storagemod.tile;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import com.tom.storagemod.Config;
import com.tom.storagemod.Content;
import com.tom.storagemod.block.IInventoryCable;
import com.tom.storagemod.block.InventoryCableConnectorBlock;
import com.tom.storagemod.tile.InventoryConnectorBlockEntity.LinkedInv;
import com.tom.storagemod.util.IProxy;
import com.tom.storagemod.util.TickerUtil.TickableServer;

public class AbstractInventoryCableConnectorBlockEntity extends PaintedBlockEntity implements TickableServer {

	public AbstractInventoryCableConnectorBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
		super(tileEntityTypeIn, pos, state);
	}

	protected InventoryConnectorBlockEntity master;
	private BlockCapabilityCache<IItemHandler, Direction> pointedAt;
	protected LinkedInv linv;
	private IItemHandler invHandler;

	@Override
	public void updateServer() {
		if(!level.isClientSide && level.getGameTime() % 20 == 19) {
			BlockState state = level.getBlockState(worldPosition);
			Direction facing = state.getValue(InventoryCableConnectorBlock.FACING);
			Stack<BlockPos> toCheck = new Stack<>();
			Set<BlockPos> checkedBlocks = new HashSet<>();
			checkedBlocks.add(worldPosition);
			toCheck.addAll(((IInventoryCable)state.getBlock()).next(level, state, worldPosition));
			if(master != null)master.unLink(linv);
			master = null;
			linv = new LinkedInv();
			initLinv();
			while(!toCheck.isEmpty()) {
				BlockPos cp = toCheck.pop();
				if(!checkedBlocks.contains(cp)) {
					checkedBlocks.add(cp);
					if(level.isLoaded(cp)) {
						state = level.getBlockState(cp);
						if(state.getBlock() == Content.connector.get()) {
							BlockEntity te = level.getBlockEntity(cp);
							if(te instanceof InventoryConnectorBlockEntity) {
								master = (InventoryConnectorBlockEntity) te;
								linv.time = level.getGameTime();
								linv.handler = this::getPointedAtHandler;
								master.addLinked(linv);
							}
							break;
						}
						if(state.getBlock() instanceof IInventoryCable) {
							toCheck.addAll(((IInventoryCable)state.getBlock()).next(level, state, cp));
						}
					}
					if(checkedBlocks.size() > Config.get().invConnectorMax)break;
				}
			}
		}
	}

	protected void initLinv() {
	}

	protected IItemHandler getPointedAtHandler() {
		IItemHandler h = pointedAt.getCapability();
		return h == null ? null : applyFilter(h);
	}

	protected IItemHandler applyFilter(IItemHandler to) {
		return to;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (!level.isClientSide) {
			BlockState state = level.getBlockState(worldPosition);
			Direction facing = state.getValue(InventoryCableConnectorBlock.FACING);
			pointedAt = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, (ServerLevel) level, worldPosition.relative(facing), facing.getOpposite());
			invHandler = applyFilter(new InvHandler());
		}
	}

	private class InvHandler implements IItemHandler, IProxy {

		private boolean calling;
		public <R> R call(Function<IItemHandler, R> func, R def) {
			if(calling)return def;
			calling = true;
			if(master != null && !master.isRemoved()) {
				R r = func.apply(master.getInventory());
				calling = false;
				return r;
			}
			calling = false;
			return def;
		}

		@Override
		public int getSlots() {
			return call(IItemHandler::getSlots, 0);
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return call(i -> i.getStackInSlot(slot), ItemStack.EMPTY);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return call(i -> i.insertItem(slot, stack, simulate), stack);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return call(i -> i.extractItem(slot, amount, simulate), ItemStack.EMPTY);
		}

		@Override
		public int getSlotLimit(int slot) {
			return call(i -> i.getSlotLimit(slot), 0);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return call(i -> i.isItemValid(slot, stack), false);
		}

		@Override
		public IItemHandler get() {
			if(master != null && !master.isRemoved()) {
				return master.getInventory();
			}
			return null;
		}
	}

	public IItemHandler getInvHandler() {
		return invHandler;
	}
}
