package dsbd.project.ordermanager.service;

import com.google.gson.Gson;
import com.netflix.discovery.EurekaClient;
import dsbd.project.ordermanager.controller.OrderRequest;
import dsbd.project.ordermanager.data.FinalOrderRepository;
import product.ProductUpdateRequest;
import order.FinalOrder;
import order.OrderProduct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import product.Product;
import user.User;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
@Transactional
public class OrderService {

    @Autowired
    FinalOrderRepository finalOrderRepository;

    @Autowired
    EurekaClient eurekaClient;

    @Value("${kafkaTopic}")
    private String topicName;

    @Autowired      //quello che facilita la pubblicazione sul topic
    private KafkaTemplate<String, String> kafkaTemplate;

    public void sendMessage(String message){
        kafkaTemplate.send(topicName, message);
    }

    public String add(OrderRequest orderRequest, int userId){ //Ci serve per ottenere X-User-ID
        String USER_MANAGER_URL=eurekaClient.getNextServerFromEureka("usermanager",false).getHomePageUrl();
        User user = new RestTemplate().getForObject(USER_MANAGER_URL + "/user/id/{userId}", User.class, userId);
        if(user!=null) {
            String PRODUCT_MANAGER_URL=eurekaClient.getNextServerFromEureka("productmanager",false).getHomePageUrl();
            List<OrderProduct> list = new ArrayList<>();
            for(Map.Entry<Integer,Integer> item: orderRequest.getProducts().entrySet()){
                Product product = new RestTemplate().getForObject(PRODUCT_MANAGER_URL+"/product/id/{id}" , Product.class, item.getKey());
                if(product.getQuantity()> item.getValue()) {
                    list.add(new OrderProduct()
                            .setProduct(product)
                            .setQuantity(item.getValue()));
                }
            }
            FinalOrder order = new FinalOrder();
            order.setUser(user);
            order.setProducts(list);
            order.setShippingAddress(orderRequest.getShippingAddress());
            order.setBillingAddress(orderRequest.getBillingAddress());
            finalOrderRepository.save(order);

            for(final OrderProduct orderProduct : list){
                Product prod = orderProduct.getProduct();
                sendMessage(new Gson().toJson(new ProductUpdateRequest()
                        .setProductId(prod.getId())
                        .setProductQuantity(orderProduct.getQuantity())));
            }

            return "Order created " + order.toString();
        }
        else
            return "The user " + userId + " is not present";
    }

    public Page<FinalOrder> getAllOrders(int userId,int per_page, int page){
        String USER_MANAGER_URL=eurekaClient.getNextServerFromEureka("usermanager",false).getHomePageUrl();
        Pageable pageWithElements = PageRequest.of(page, per_page);
        if(userId!=0) {
            User user = new RestTemplate().getForObject(USER_MANAGER_URL + "/user/id/{userId}", User.class, userId);
            Page<FinalOrder> order = finalOrderRepository.findAllByUser(Optional.ofNullable(user), pageWithElements);
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

    public Optional<FinalOrder> getId(Integer id, int userId) {
        String USER_MANAGER_URL=eurekaClient.getNextServerFromEureka("usermanager",false).getHomePageUrl();
        if(userId!=0) {
            User user = new RestTemplate().getForObject(USER_MANAGER_URL + "/user/id/{userId}", User.class, userId);
            Optional<FinalOrder> order = finalOrderRepository.findFinalOrderByIdAndUser(id, Optional.ofNullable(user));
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
