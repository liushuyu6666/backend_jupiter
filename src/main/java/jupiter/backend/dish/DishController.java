package jupiter.backend.dish;

import jupiter.backend.core.AuthenticationService;
import jupiter.backend.dump.dish.DumpDish;
import jupiter.backend.dump.dish.DumpDishService;
import jupiter.backend.exception.FailToDeleteDocument;
import jupiter.backend.exception.IllegalFormat;
import jupiter.backend.exception.NoSuchDocument;
import jupiter.backend.exception.RedundantIssueException;
import jupiter.backend.payload.response.MessageResponse;
import jupiter.backend.payload.response.ResponseBody;
import jupiter.backend.shop.Shop;
import jupiter.backend.shop.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/v1/jupiter")
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
public class DishController {

    @Autowired
    AuthenticationService authenticationService;

    @Autowired
    DishService dishService;

    @Autowired
    ShopService shopService;

    @Autowired
    DumpDishService dumpDishService;

    @PostMapping("/dishes")
    @PreAuthorize("hasRole('ROLE_OWNER')")
    public ResponseEntity<?> createDish(
            Authentication authentication,
            @RequestBody Dish newDish
    ){
        if(newDish.getName().trim().equals("")){
            return IllegalFormat.badRequest("dish's name can't be empty or whitespace");
        }
        if(dishService.existsByShopIdAndName(newDish.getShopId(), newDish.getName())){
            return RedundantIssueException.ok("dish's name existed in this shop");
        }
        String userId = authenticationService.parseAuthenticationGetId(authentication);
        if(!shopService.existsByIdAndOwnerId(newDish.getShopId(), userId)){
            return NoSuchDocument.ok("no such shop under the user");
        }
        Dish savedDish = dishService.createDish(newDish, userId);
        ResponseBody responseBody
                = new ResponseBody(savedDish,
                "create dish successfully",
                null);
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/shops/{shopId}/dishes/{dishId}")
    public ResponseEntity<?> retrieveDish(
            @PathVariable("shopId") String shopId,
            @PathVariable("dishId") String dishId
    ){
        if(!shopService.existsById(shopId)){
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("no such shop"));
        }
        if(!dishService.existsByIdAndShopId(dishId, shopId)){
            return NoSuchDocument.ok("no such dish under this restaurant");
        }
        Dish dish = dishService.findByShopIdAndId(shopId, dishId);
        ResponseBody responseBody = new ResponseBody(dish, "retrieve it", null);
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/shops/{shopId}/dishes")
    public ResponseEntity<?> listDish(
            @PathVariable("shopId") String shopId
    ){
        if(!shopService.existsById(shopId)){
            return NoSuchDocument.ok("no such shop");
        }
        List<Dish> dishesList = dishService.listDishes(shopId);
        ResponseBody responseBody
                = new ResponseBody(dishesList,
                String.format("list all dishes under shopId %s", shopId),
                null);
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/manage/shops/{shopId}/dishes")
    @PreAuthorize("hasRole('ROLE_OWNER')")
    public ResponseEntity<?> listDishUnderOwner(
            Authentication authentication,
            @PathVariable("shopId") String shopId
    ){
        String userId = authenticationService.parseAuthenticationGetId(authentication);
        Shop targetShop = shopService.findShopByIdAndOwnerId(shopId, userId).orElse(null);
        if(targetShop == null){
            return NoSuchDocument.ok("no such shop under this user");
        }
        List<Dish> dishesList = dishService.listDishes(targetShop.getId());
        ResponseBody responseBody
                = new ResponseBody(dishesList,
                String.format("list all dishes under shopId %s", shopId),
                null);
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/dishes/{dishId}")
    @PreAuthorize("hasRole('ROLE_OWNER')")
    public ResponseEntity<?> updateDish(
            Authentication authentication,
            @PathVariable("dishId") String dishId,
            @RequestBody Dish updatingDish
    ){
        String userId = authenticationService.parseAuthenticationGetId(authentication);
        String shopId = updatingDish.getShopId();
        if(shopId == null){
            return IllegalFormat.badRequest("shopId can't be empty");
        }
        if(!dishService.existsByIdAndShopIdAndOwnerId(dishId, shopId, userId)){
            return NoSuchDocument.ok("no such dish");
        }
        if(updatingDish.getName().trim().equals("")){
            return IllegalFormat.badRequest("new dish's name can't be empty or whitespace");
        }
        if(dishService.otherExistsByDishName(
                shopId,
                dishId,
                updatingDish.getName())){
            return RedundantIssueException.ok("new dish's name existed");
        }
        updatingDish.setId(dishId);
        Dish dish = dishService.updateDish(updatingDish);
        ResponseBody responseBody = new ResponseBody(dish, "update dish", null);
        return ResponseEntity.ok(responseBody);
    }

    @DeleteMapping("/dishes/{dishId}")
    @PreAuthorize("hasRole('ROLE_OWNER')")
    public ResponseEntity<?> deleteDish(
            Authentication authentication,
            @PathVariable String dishId
    ){
        String ownerId = authenticationService.parseAuthenticationGetId(authentication);
        Dish targetDish = dishService.findByIdAndOwnerId(dishId, ownerId).orElse(null);
        if(targetDish == null){
            return NoSuchDocument.ok("no such dish under the owner");
        }
        if(dishService.deleteDish(dishId)){
            DumpDish savedDumpDish = dumpDishService.createDumpDish(targetDish);
            ResponseBody responseBody
                    = new ResponseBody(
                    savedDumpDish,
                    "delete successfully",
                    null);
            return ResponseEntity.ok(responseBody);
        }
        else return FailToDeleteDocument.ok(String.format("dishId: %s", dishId));
    }
}
