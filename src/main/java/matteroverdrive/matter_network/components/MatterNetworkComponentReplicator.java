/*
 * This file is part of Matter Overdrive
 * Copyright (c) 2015., Simeon Radivoev, All rights reserved.
 *
 * Matter Overdrive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Matter Overdrive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Matter Overdrive.  If not, see <http://www.gnu.org/licenses>.
 */

package matteroverdrive.matter_network.components;

import cofh.lib.util.TimeTracker;
import cofh.lib.util.position.BlockPosition;
import cpw.mods.fml.common.gameevent.TickEvent;
import matteroverdrive.Reference;
import matteroverdrive.api.network.IMatterNetworkDispatcher;
import matteroverdrive.api.network.MatterNetworkTask;
import matteroverdrive.api.network.MatterNetworkTaskState;
import matteroverdrive.matter_network.MatterNetworkPacket;
import matteroverdrive.matter_network.MatterNetworkTaskQueue;
import matteroverdrive.matter_network.packets.MatterNetworkRequestPacket;
import matteroverdrive.matter_network.packets.MatterNetworkTaskPacket;
import matteroverdrive.matter_network.packets.MatterNetworkResponsePacket;
import matteroverdrive.matter_network.tasks.MatterNetworkTaskReplicatePattern;
import matteroverdrive.tile.TileEntityMachineReplicator;
import matteroverdrive.util.MatterDatabaseHelper;
import matteroverdrive.util.MatterNetworkHelper;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Created by Simeon on 7/13/2015.
 */
public class MatterNetworkComponentReplicator extends MatterNetworkComponentClient implements IMatterNetworkDispatcher
{
    private TileEntityMachineReplicator replicator;
    private TimeTracker patternSearchTracker;

    public MatterNetworkComponentReplicator(TileEntityMachineReplicator replicator)
    {
        this.replicator = replicator;
        patternSearchTracker = new TimeTracker();
    }

    @Override
    public boolean canPreform(MatterNetworkPacket packet) {
        if (packet instanceof MatterNetworkTaskPacket)
        {
            if (((MatterNetworkTaskPacket) packet).getTask(replicator.getWorldObj()) instanceof MatterNetworkTaskReplicatePattern)
            {
                return replicator.getQueue(0).remaintingCapacity() > 0;
            }
        }else if (packet instanceof MatterNetworkRequestPacket)
        {
            return ((MatterNetworkRequestPacket) packet).getRequestType() == Reference.PACKET_REQUEST_CONNECTION || ((MatterNetworkRequestPacket) packet).getRequestType() == Reference.PACKET_REQUEST_NEIGHBOR_CONNECTION;
        }
        return false;
    }

    @Override
    public void queuePacket(MatterNetworkPacket packet, ForgeDirection from)
    {
        packet.addToPath(replicator, from);

        if (packet instanceof MatterNetworkTaskPacket)
        {
            manageTaskPackets((MatterNetworkTaskPacket)packet,((MatterNetworkTaskPacket) packet).getTask(replicator.getWorldObj()));
        }else if (packet instanceof MatterNetworkResponsePacket)
        {
            manageResponses((MatterNetworkResponsePacket) packet);
        }
        else if (packet instanceof MatterNetworkRequestPacket)
        {
            manageRequestPackets(replicator, replicator.getWorldObj(), (MatterNetworkRequestPacket) packet, from);
        }
    }

    private void manageTaskPackets(MatterNetworkTaskPacket packet,MatterNetworkTask task)
    {
        if (task instanceof MatterNetworkTaskReplicatePattern)
        {
            if (replicator.getQueue(0).queue((MatterNetworkTaskReplicatePattern)task))
            {
                task.setState(MatterNetworkTaskState.QUEUED);
                task.setAlive(true);
                replicator.ForceSync();
            }
        }
    }

    private void manageResponses(MatterNetworkResponsePacket packet)
    {
        //Request pattern search response
        if (packet.getRequestType() == Reference.PACKET_REQUEST_PATTERN_SEARCH && packet.getResponseType() == Reference.PACKET_RESPONCE_VALID)
        {
            NBTTagCompound responseTag = packet.getResponse();
            MatterNetworkTaskReplicatePattern task = replicator.getQueue(0).peek();
            if (responseTag != null && responseTag.getShort("id") == task.getItemID() && responseTag.getShort("Damage") == task.getItemMetadata())
            {
                if (replicator.getInternalPatternStorage() != null)
                {
                    //if the previous tag is the same but has a higher progress, then continue
                    if (replicator.getInternalPatternStorage().getShort("id") == responseTag.getShort("id")
                            && replicator.getInternalPatternStorage().getShort("Damage") == responseTag.getShort("Damage")
                            && MatterDatabaseHelper.GetProgressFromNBT(replicator.getInternalPatternStorage()) > MatterDatabaseHelper.GetProgressFromNBT(responseTag))
                    {
                        return;
                    }
                }

                //save the pattern in the machine
                replicator.setInternalPatternStorage(responseTag);
                replicator.ForceSync();
            }
        }
    }

    @Override
    public MatterNetworkTaskQueue<? extends MatterNetworkTask> getQueue(int queueID) {
        return replicator.getQueue(queueID);
    }

    @Override
    public BlockPosition getPosition() {
        return replicator.getPosition();
    }

    @Override
    public boolean canConnectFromSide(ForgeDirection side) {
        return replicator.canConnectFromSide(side);
    }

    @Override
    public int onNetworkTick(World world, TickEvent.Phase phase)
    {
        if (phase.equals(TickEvent.Phase.END))
        {
            return managePatternSearch(world);
        }
        return 0;
    }

    private int managePatternSearch(World world)
    {
        int broadcasts = 0;

        MatterNetworkTaskReplicatePattern replicatePattern = replicator.getQueue(0).peek();

        if (replicator.getRedstoneActive() && !replicator.canCompleteTask(replicatePattern) && patternSearchTracker.hasDelayPassed(replicator.getWorldObj(),replicator.PATTERN_SEARCH_DELAY)) {
            if (replicatePattern != null) {
                for (int i = 0; i < 6; i++) {
                    MatterNetworkRequestPacket requestPacket = new MatterNetworkRequestPacket(replicator, Reference.PACKET_REQUEST_PATTERN_SEARCH,ForgeDirection.getOrientation(i), new int[]{replicatePattern.getItemID(), replicatePattern.getItemMetadata()});
                    if (MatterNetworkHelper.broadcastTaskInDirection(world, requestPacket, replicator, ForgeDirection.getOrientation(i)))
                    {
                        broadcasts++;
                    }
                }
            }
        }
        return broadcasts;
    }
}
