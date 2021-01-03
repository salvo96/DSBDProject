package dsbd.project.monolithic_order.Api;

import dsbd.project.monolithic_order.DataModel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Controller
@RequestMapping(path="/order")
public class OrderController {

    @Autowired
    FinalOrderRepository finalOrderRepository;

    //Provvisorio: la modificheremo con i microservizi
    @Autowired
    UserRepository userRepository;

    //Provvisorio: la modificheremo con i microservizi
    @Autowired
    ProductRepository productRepository;

    @PostMapping(path="/orders")
    public @ResponseBody String add(@RequestBody OrderRequest orderRequest, @RequestHeader("X-User-ID") int userId){ //Ci serve per ottenere X-User-ID
        Optional<User> user= userRepository.findById(userId);
        //Ciò verrà fatto atomicamente nel service
        if(user.isPresent()) {
            List<OrderProduct> list = new ArrayList<>();
            for(Map.Entry<Integer,Integer> item: orderRequest.getProducts().entrySet()){
                Product product=productRepository.findByIdAndQuantityGreaterThanEqual(item.getKey(),item.getValue());
                list.add(new OrderProduct()
                        .setProduct(product)
                        .setQuantity(item.getValue()));
                product.setQuantity(product.getQuantity() - item.getValue());
            }
            FinalOrder order = new FinalOrder();
            order.setUser(user.get());
            order.setProducts(list);
            order.setShippingAddress(orderRequest.getShippingAddress());
            order.setBillingAddress(orderRequest.getBillingAddress());
            finalOrderRepository.save(order);
            return "Order created " + order.toString();
        }
        else
            return "The user " + userId + " is not present";

    }

    @GetMapping(path ="/orders", params = {"per_page", "page"})
    public @ResponseBody Page<FinalOrder> getAllOrders(@RequestHeader("X-User-ID") int userId,
        @RequestParam("per_page") int per_page, @RequestParam("page") int page){

        Pageable pageWithElements = PageRequest.of(page, per_page);
        if(userId!=0) {
            Page<FinalOrder> order = finalOrderRepository.findAllByUser(userRepository.findById(userId), pageWithElements);
            if(StreamSupport.stream(order.spliterator(), false).count()>0)
                return order;
            else
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        else {
            Page<FinalOrder> order = finalOrderRepository.findAll(pageWithElements); //findAll(pageWithElements);
            if(StreamSupport.stream(order.spliterator(), false).count()>0)
                return order;
            else
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(path="/orders/{id}")
    public @ResponseBody Optional<FinalOrder> getId(@PathVariable Integer id, @RequestHeader("X-User-ID") int userId) {
        if(userId!=0) {
            Optional<FinalOrder> order = finalOrderRepository.findFinalOrderByIdAndUser(id, userRepository.findById(userId));
            if(order.isPresent())
                return order;
            else
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        else {
            Optional<FinalOrder> order = finalOrderRepository.findFinalOrderById(id);
            if(order.isPresent())
                return order;
            else
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }


}
