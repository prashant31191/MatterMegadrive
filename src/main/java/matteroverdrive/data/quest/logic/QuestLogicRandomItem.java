package matteroverdrive.data.quest.logic;

import matteroverdrive.data.quest.QuestItem;
import matteroverdrive.data.quest.QuestStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Simeon on 12/24/2015.
 */
public abstract class QuestLogicRandomItem extends AbstractQuestLogic
{
    QuestItem[] items;
    boolean randomItem;

    protected void init(QuestItem[] questItems)
    {
        this.items = questItems;
        this.randomItem = true;
    }

    protected void initItemType(Random random, QuestStack questStack)
    {
        if (randomItem)
        {
            List<Integer> avalibleBlocks = new ArrayList<>();
            for (int i = 0;i < items.length;i++)
            {
                ItemStack itemStack = items[i].getItemStack();
                if (itemStack != null)
                {
                    avalibleBlocks.add(i);
                }
            }
            if (avalibleBlocks.size() > 0)
            {
                setItemType(questStack,avalibleBlocks.get(random.nextInt(avalibleBlocks.size())));
            }
        }else
        {
            for (int i = 0;i < items.length;i++)
            {
                ItemStack itemStack = items[i].getItemStack();
                if (itemStack != null)
                {
                    setItemType(questStack,i);
                    return;
                }
            }
        }
    }

    @Override
    public boolean canAccept(QuestStack questStack, EntityPlayer entityPlayer)
    {
        for (QuestItem item : items)
        {
            if (!item.canItemExist())
            {
                return false;
            }
        }
        return true;
    }

    public ItemStack getItem(QuestStack questStack)
    {
        return items[getItemType(questStack)].getItemStack();
    }

    public int getItemType(QuestStack questStack)
    {
        if (questStack.getTagCompound() != null)
        {
            return questStack.getTagCompound().getByte("ItemType");
        }
        return 0;
    }

    public void setItemType(QuestStack questStack,int itemType)
    {
        if (questStack.getTagCompound() == null)
            questStack.setTagCompound(new NBTTagCompound());

        questStack.getTagCompound().setByte("ItemType",(byte) itemType);
    }

    public QuestLogicRandomItem setRandomItem(boolean randomItem)
    {
        this.randomItem = randomItem;
        return this;
    }
}
