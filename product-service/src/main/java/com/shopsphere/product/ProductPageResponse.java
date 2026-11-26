package com.shopsphere.product;
import java.util.List;
record ProductPageResponse(List<ProductResponse> content,int number,int size,long totalElements,int totalPages){}
