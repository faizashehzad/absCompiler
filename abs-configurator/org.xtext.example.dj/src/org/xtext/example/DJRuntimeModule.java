/*
 * generated by Xtext
 */
package org.xtext.example;

import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceFactory;
import org.xtext.example.linking.DjLinkingResource;
import org.xtext.example.linking.DjResourceFactory;

/**
 * Use this class to register components to be used within the IDE.
 */
public class DJRuntimeModule extends org.xtext.example.AbstractDJRuntimeModule {
	@Override
	public Class<? extends XtextResource> bindXtextResource() {
		return DjLinkingResource.class;
	}
	
	/*public Class<? extends IValueConverterService> bindIValueConverterService() {
		return DJValueConverter.class;
	}*/
	
	/* (non-Javadoc)
	 * @see org.eclipse.xtext.service.DefaultRuntimeModule#bindIResourceFactory()
	 */
	public Class<? extends XtextResourceFactory> bindXtextResourceFactory() {
		  return DjResourceFactory.class;
	}
}