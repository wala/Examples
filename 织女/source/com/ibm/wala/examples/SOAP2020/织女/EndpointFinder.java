package com.ibm.wala.examples.SOAP2020.织女;

@FunctionalInterface
public interface EndpointFinder<T> {
	
	boolean endpoint(T s);
	
}