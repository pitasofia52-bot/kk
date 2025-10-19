package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.instance.Player;


/**
 * @author SweeTs
 */

public class HeroItem implements IItemHandler
{
   @Override
   public void useItem(Playable playable, ItemInstance item, boolean forceUse)
   {
      if (!(playable instanceof Player))
         return;
           
      int itemId = item.getItemId();
      
      if (itemId == 10648) // Item to become hero
      {
          ((Player)playable).destroyItem("", item, null, true);
          ((Player)playable).setHero(true);
          ((Player)playable).broadcastUserInfo();
      }
   }
}
