/*
 * Created on May 30, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.dotmarketing.business;

import com.dotcms.uuid.shorty.ShortyIdCache;
import com.dotcms.variant.VariantAPI;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.VersionInfo;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.portlets.contentlet.model.ContentletVersionInfo;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

import java.util.List;

/**
 * @author David
 * 
 */
public class IdentifierCacheImpl extends IdentifierCache { 

	DotCacheAdministrator cache = null;

	protected IdentifierCacheImpl() {

		cache = CacheLocator.getCacheAdministrator();
	}

	@Override
	protected void addIdentifierToCache(Identifier id, Versionable v) {
		if(v!=null &&v.getInode() !=null){
			cache.put(getVersionGroup() + v.getInode(), id.getId(), getVersionGroup());
		}
	}
	
	@Override
	protected void addIdentifierToCache(String identifier, String inode) {
		if(UtilMethods.isSet(identifier) && InodeUtils.isSet(inode)){
			cache.put(getVersionGroup() + inode, identifier, getVersionGroup());
		}
	}
	
	
	private String getIdentGroup(Identifier id) {
	    return id.getAssetType()!=null && id.getAssetType().equals(IdentifierAPI.IDENT404) ? 
	            get404Group() : getPrimaryGroup();
	}
	
	
	protected void addIdentifierToCache(Identifier id) {
		if (id == null) {
			return;
		}
		
		String uri = id.getURI();
		if(UtilMethods.isSet(id.getHostId()) && UtilMethods.isSet(uri)) {
        	// Obtain the key for the new entrance
        	String key = getPrimaryGroup() + id.getHostId() + "-" + uri;
        	cache.put(key, id, getIdentGroup(id));
		}

		if(InodeUtils.isSet(id.getId())) {
		    cache.put(getPrimaryGroup() + id.getId(), id, getIdentGroup(id));
		}
		
	}
	
	protected Identifier getIdentifier(Host host,String URI) {
		if(host ==null) return null;
		return getIdentifier(host.getIdentifier(), URI);
	}

	/**
	 * This method find the identifier associated with the given URI this
	 * methods will try to find the identifier in memory but if it is not found
	 * in memory it'll be found in db and put in memory.
	 * 
	 * @param URI
	 *            uri of the identifier
	 * @param hostId
	 *            host where the identifier belongs
	 * @return The identifier or an empty (inode = 0) identifier if it wasn't
	 *         found in momory and db.
	 */
	protected Identifier getIdentifier( String hostId, String URI ) {

		Identifier value = null;
		try {
		    final String key = getPrimaryGroup() + hostId + "-" + URI;
			value = (Identifier) cache.get(key, getPrimaryGroup());
			if(value ==null) {
			    value = (Identifier) cache.get(key, get404Group());
			}
		} catch (DotCacheException e) {
			Logger.debug(IdentifierCacheImpl.class, "Cache Entry not found", e);
		}

		return value;
	}


	protected Identifier getIdentifier(String identId)  {

		Identifier value = null;
		try {
		    final String key = getPrimaryGroup() + identId;
			value = (Identifier) cache.get(key, getPrimaryGroup());
			if(value==null) {
			    value = (Identifier) cache.get(key, get404Group());
			}
		} catch (DotCacheException e) {
			Logger.debug(IdentifierCacheImpl.class, "Cache Entry not found", e);
		}

		return value;
	}

	protected String getIdentifierFromInode(Versionable versionable)  {

		if(versionable ==null || !InodeUtils.isSet(versionable.getInode())){
			return null;
		}
		
		String value = null;
		
		try {
			value = (String) cache.get(getVersionGroup() + versionable.getInode(), getVersionGroup());
		} catch (DotCacheException e) {
			Logger.debug(IdentifierCacheImpl.class, "Cache Entry not found", e);
		}
		
		return value;
	}
	
	protected String getIdentifierFromInode(String inode)  {

		if(!InodeUtils.isSet(inode)){
			return null;
		}
		
		String value = null;
		
		try {
			value = (String) cache.get(getVersionGroup() + inode, getVersionGroup());
		} catch (DotCacheException e) {
			Logger.debug(IdentifierCacheImpl.class, "Cache Entry not found", e);
		}
		
		return value;
	}
	

	protected void removeFromCacheByIdentifier(final Identifier id) {
		if(id==null) {
			return;
		}

		if(UtilMethods.isSet(id::getId)) {
		    final String key = getPrimaryGroup() + id.getId();
            cache.remove(key,  getPrimaryGroup());
		    cache.remove(key,  get404Group());
			cache.remove(allInfosKey(id.getId()), getVersionInfoGroup());
		}
		
		final String uri = id.getURI();
		if(UtilMethods.isSet(id::getHostId) && UtilMethods.isSet(uri)) {
    		final String key = getPrimaryGroup() + id.getHostId() + "-" + uri;
    		cache.remove(key, getPrimaryGroup());
    		cache.remove(key, get404Group());
		}
		
		if(UtilMethods.isSet(id.getAssetType()) && id.getAssetType().equals("folder")) {
		    try {

		        final List<Identifier> identifiers = APILocator.getIdentifierAPI()
						.findByParentPath(id.getHostId(), id.getURI());

		        for(final Identifier identifier : identifiers) {
					removeFromCacheByIdentifier(identifier);
				}
		    }
		    catch(Exception ex) {
		        Logger.warn(this, ex.getMessage(),ex);
		    }
		}
		
		new ShortyIdCache().remove(id.getId());
	}
	
	public void removeFromCacheByIdentifier(String ident) {
		
		Identifier id = getIdentifier(ident);
		if(id==null){
			id=new Identifier();
			id.setId(ident);
		}
		
		removeFromCacheByIdentifier(id);

	}
	


	protected void removeFromCacheByURI(String hostId,String URI) {
		Identifier id = getIdentifier(hostId,URI);
		if(id==null) {
    		String key = getPrimaryGroup() + hostId + "-" + URI;
    		cache.remove(key, getPrimaryGroup());
    		cache.remove(key, get404Group());
		}
		else {
		    removeFromCacheByIdentifier(id);
		}

	}

	 public void removeFromCacheByVersionable(Versionable versionable) {
		
		removeFromCacheByIdentifier(versionable.getVersionId());

	}
	 
	 public void removeFromCacheByInode(String inode) {
		 cache.remove(getVersionGroup() + inode, getVersionGroup());
		 cache.remove(allInfosKey(inode), getVersionInfoGroup());
	 }
	
	
	public void clearCache() {
		// clear the cache
	    for(String group : getGroups()) {
	        cache.flushGroup(group);
	    }
	}

    @Override
    protected void addContentletVersionInfoToCache(final ContentletVersionInfo contV) {
        final String key = getKey(contV);
        cache.put(getVersionInfoGroup()+key, contV, getVersionInfoGroup());
    }

	private String getKey(final ContentletVersionInfo contV) {
		return getKey(contV.getIdentifier(), contV.getLang(), contV.getVariant());
	}

	private String getKey(final String identifier, final long lang, final String variantId) {
		return String.format("%s-lang:%s-variant:%s", identifier, lang, variantId);
	}

	private String getKey(final String identifier, final long lang) {
		return getKey(identifier, lang, VariantAPI.DEFAULT_VARIANT.name());
	}

	@Override
    protected void addVersionInfoToCache(VersionInfo versionInfo) {
        String key=versionInfo.getIdentifier();
        cache.put(getVersionInfoGroup()+key, versionInfo, getVersionInfoGroup());
    }

	@Override
	protected ContentletVersionInfo getContentVersionInfo(final String identifier,
			final long lang, final String variantId){
		ContentletVersionInfo contV = null;
		try {
			String key= getKey(identifier, lang, variantId);
			contV = (ContentletVersionInfo)cache.get(getVersionInfoGroup()+key, getVersionInfoGroup());
		}
		catch(Exception ex) {
			Logger.debug(this, identifier +" contentVersionInfo not found in cache");
		}
		return contV;
	}

	@Override
    protected ContentletVersionInfo getContentVersionInfo(final String identifier, final long lang) {
		return getContentVersionInfo(identifier, lang, VariantAPI.DEFAULT_VARIANT.name());
    }

    @Override
    public VersionInfo getVersionInfo(String identifier) {
        VersionInfo vi = null;
        try {
            vi = (VersionInfo)cache.get(getVersionInfoGroup()+identifier, getVersionInfoGroup());
        }
        catch(Exception ex) {
            Logger.debug(this, identifier +" versionInfo not found in cache");
        }
        return vi;
    }

    @Override
    public void removeContentletVersionInfoToCache(String identifier, long lang) {
        String key = getKey(identifier, lang);
        cache.remove(getVersionInfoGroup()+key, getVersionInfoGroup());
		cache.remove( allInfosKey(identifier) ,getVersionInfoGroup());
    }

	@Override
	public void removeContentletVersionInfoToCache(String identifier, long lang, final String variantId) {
		String key = getKey(identifier, lang, variantId);
		cache.remove(getVersionInfoGroup() + key , getVersionInfoGroup());
		cache.remove( allInfosKey(identifier) ,getVersionInfoGroup());
	}

    @Override
    protected void removeVersionInfoFromCache(String identifier) {
        cache.remove(getVersionInfoGroup()+identifier, getVersionInfoGroup());
		cache.remove( allInfosKey(identifier) ,getVersionInfoGroup());
    }

	private String allInfosKey(String... vals){
		return "all-"+ String.join("-", vals);
	}


	@Override
	public List<ContentletVersionInfo> getContentVersionInfos(final String identifier){
		// we don't read from cache in transactions
		if(DbConnectionFactory.inTransaction()){
			return null;
		}
		return (List<ContentletVersionInfo>) cache.getNoThrow(allInfosKey(identifier) , getVersionInfoGroup());
	}

	@Override
	public void putContentVersionInfos(final String identifier, final List<ContentletVersionInfo> versionInfos){
		// we don't write cache in transactions
		if(DbConnectionFactory.inTransaction()){
			return;
		}
		cache.put(allInfosKey(identifier) ,versionInfos, getVersionInfoGroup());
	}
}
