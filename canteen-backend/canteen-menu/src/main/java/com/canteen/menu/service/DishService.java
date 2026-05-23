package com.canteen.menu.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.canteen.common.exception.BusinessException;
import com.canteen.common.result.ResultCode;
import com.canteen.menu.dto.DishCreateRequest;
import com.canteen.menu.dto.DishVO;
import com.canteen.menu.entity.Dish;
import com.canteen.menu.entity.Merchant;
import com.canteen.menu.mapper.DishMapper;
import com.canteen.menu.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {

    private final DishMapper dishMapper;
    private final MerchantMapper merchantMapper;

    public Page<DishVO> listDishes(Long merchantId, int page, int size) {
        Page<Dish> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(Dish::getMerchantId, merchantId);
        }
        wrapper.orderByDesc(Dish::getCreatedAt);

        Page<Dish> result = dishMapper.selectPage(pageParam, wrapper);
        Page<DishVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    public DishVO createDish(DishCreateRequest req) {
        Merchant merchant = merchantMapper.selectById(req.getMerchantId());
        if (merchant == null) {
            throw new BusinessException(ResultCode.MENU_MERCHANT_NOT_FOUND);
        }

        Dish dish = new Dish();
        BeanUtil.copyProperties(req, dish);
        dish.setOnShelf(1);
        dishMapper.insert(dish);

        log.info("Dish created: id={}, name={}, merchantId={}", dish.getId(), dish.getName(), dish.getMerchantId());
        return toVO(dish);
    }

    public DishVO updateDish(Long id, DishCreateRequest req) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(ResultCode.MENU_DISH_NOT_FOUND);
        }

        dish.setName(req.getName());
        dish.setPriceCents(req.getPriceCents());
        dish.setImageUrl(req.getImageUrl());
        if (req.getThreshold() != null) {
            dish.setThreshold(req.getThreshold());
        }
        dishMapper.updateById(dish);

        log.info("Dish updated: id={}", id);
        return toVO(dish);
    }

    public void toggleShelf(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(ResultCode.MENU_DISH_NOT_FOUND);
        }
        dish.setOnShelf(dish.getOnShelf() == 1 ? 0 : 1);
        dishMapper.updateById(dish);
        log.info("Dish shelf toggled: id={}, onShelf={}", id, dish.getOnShelf());
    }

    public DishVO getDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(ResultCode.MENU_DISH_NOT_FOUND);
        }
        return toVO(dish);
    }

    private DishVO toVO(Dish dish) {
        DishVO vo = new DishVO();
        BeanUtil.copyProperties(dish, vo);
        return vo;
    }
}
