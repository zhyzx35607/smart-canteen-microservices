package com.canteen.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.menu.dto.DailyMenuPublishRequest;
import com.canteen.menu.dto.DailyMenuVO;
import com.canteen.menu.dto.StockDeductRequest;
import com.canteen.menu.entity.DailyMenu;
import com.canteen.menu.entity.DailyMenuItem;
import com.canteen.menu.entity.Dish;
import com.canteen.menu.entity.Merchant;
import com.canteen.menu.mapper.DailyMenuItemMapper;
import com.canteen.menu.mapper.DailyMenuMapper;
import com.canteen.menu.mapper.DishMapper;
import com.canteen.menu.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyMenuService {

    private final DailyMenuMapper dailyMenuMapper;
    private final DailyMenuItemMapper dailyMenuItemMapper;
    private final DishMapper dishMapper;
    private final MerchantMapper merchantMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_KEY_PREFIX = "stock:";

    @Transactional
    public DailyMenuVO publishMenu(DailyMenuPublishRequest req) {
        // 检查同日同商户是否已发布
        Long count = dailyMenuMapper.selectCount(
                new LambdaQueryWrapper<DailyMenu>()
                        .eq(DailyMenu::getMerchantId, req.getMerchantId())
                        .eq(DailyMenu::getBizDate, req.getBizDate()));
        if (count > 0) {
            throw new BusinessException(ResultCode.MENU_ALREADY_PUBLISHED);
        }

        Merchant merchant = merchantMapper.selectById(req.getMerchantId());
        if (merchant == null) {
            throw new BusinessException(ResultCode.MENU_MERCHANT_NOT_FOUND);
        }

        DailyMenu menu = new DailyMenu();
        menu.setMerchantId(req.getMerchantId());
        menu.setBizDate(req.getBizDate());
        menu.setSellStart(req.getSellStart());
        menu.setSellEnd(req.getSellEnd());
        dailyMenuMapper.insert(menu);

        // 写入菜单条目 + Redis 库存
        for (DailyMenuPublishRequest.MenuItem item : req.getItems()) {
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish == null) {
                throw new BusinessException(ResultCode.MENU_DISH_NOT_FOUND, "dishId=" + item.getDishId());
            }

            DailyMenuItem menuItem = new DailyMenuItem();
            menuItem.setDailyMenuId(menu.getId());
            menuItem.setDishId(item.getDishId());
            menuItem.setStockInit(item.getStockInit());
            menuItem.setStockLeft(item.getStockInit());
            dailyMenuItemMapper.insert(menuItem);

            // 初始化 Redis 库存
            stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + item.getDishId(), String.valueOf(item.getStockInit()));
        }

        log.info("Daily menu published: menuId={}, merchantId={}, date={}", menu.getId(), req.getMerchantId(), req.getBizDate());
        return getMenuDetail(menu.getId());
    }

    public DailyMenuVO getTodayMenu(Long merchantId) {
        LocalDate today = LocalDate.now();
        DailyMenu menu = dailyMenuMapper.selectOne(
                new LambdaQueryWrapper<DailyMenu>()
                        .eq(DailyMenu::getMerchantId, merchantId)
                        .eq(DailyMenu::getBizDate, today));
        if (menu == null) {
            return null;
        }
        return getMenuDetail(menu.getId());
    }

    public DailyMenuVO getMenuDetail(Long menuId) {
        DailyMenu menu = dailyMenuMapper.selectById(menuId);
        if (menu == null) return null;

        Merchant merchant = merchantMapper.selectById(menu.getMerchantId());

        List<DailyMenuItem> items = dailyMenuItemMapper.selectList(
                new LambdaQueryWrapper<DailyMenuItem>().eq(DailyMenuItem::getDailyMenuId, menuId));

        DailyMenuVO vo = new DailyMenuVO();
        vo.setId(menu.getId());
        vo.setMerchantId(menu.getMerchantId());
        vo.setMerchantName(merchant != null ? merchant.getName() : "");
        vo.setBizDate(menu.getBizDate());
        vo.setSellStart(menu.getSellStart());
        vo.setSellEnd(menu.getSellEnd());

        List<DailyMenuVO.MenuItemVO> itemVOs = new ArrayList<>();
        for (DailyMenuItem item : items) {
            Dish dish = dishMapper.selectById(item.getDishId());
            DailyMenuVO.MenuItemVO itemVO = new DailyMenuVO.MenuItemVO();
            itemVO.setDishId(item.getDishId());
            itemVO.setDishName(dish != null ? dish.getName() : "");
            itemVO.setPriceCents(dish != null ? dish.getPriceCents() : 0);
            itemVO.setStockInit(item.getStockInit());
            itemVO.setStockLeft(item.getStockLeft());
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        return vo;
    }
}
