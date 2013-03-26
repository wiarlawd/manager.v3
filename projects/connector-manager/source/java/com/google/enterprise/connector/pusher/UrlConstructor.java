// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.pusher;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.enterprise.connector.manager.Context;
import com.google.enterprise.connector.servlet.ServletUtil;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.SpiConstants.DocumentType;
import com.google.enterprise.connector.spi.SpiConstants.FeedType;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Extracts specific URLs from a {@link Document} or constructs a URL
 * from various document properties.
 */
public class UrlConstructor {
  private final String dataSource;
  private final FeedType defaultFeedType;
  private final String contentUrlPrefix;

  /**
   * Gets a new UrlConstructor.
   *
   * @param dataSource the dataSource for documents
   * @param defaultFeedType the FeedType to use if a document does not
   *        specify an explicit FeedType
   */
  public UrlConstructor(String dataSource, FeedType defaultFeedType) {
    this.dataSource = dataSource;
    this.defaultFeedType = defaultFeedType;
    this.contentUrlPrefix = Context.getInstance().getContentUrlPrefix();
  }

  /**
   * Constructs the record URL for the given doc id, feed type and search URL.
   *
   * @throws RepositoryDocumentException if searchUrl is invalid.
   */
  public String getRecordUrl(Document document, DocumentType documentType)
      throws RepositoryException, RepositoryDocumentException {
    String url = getOrConstructUrl(document, SpiConstants.PROPNAME_SEARCHURL,
        SpiConstants.PROPNAME_DOCID, defaultFeedType, documentType);
    if (url == null) {
      throw new RepositoryDocumentException(
          "Document has neither property " + SpiConstants.PROPNAME_DOCID
          + " nor property " + SpiConstants.PROPNAME_SEARCHURL);
    }
    return url;
  }

  /**
   * Constructs the record URL for the inherited ACL document.
   *
   * @return inheritFrom URL, or null if there is none.
   */
  public String getInheritFromUrl(Document document)
      throws RepositoryException, RepositoryDocumentException {
    return getOrConstructUrl(document, SpiConstants.PROPNAME_ACLINHERITFROM,
        SpiConstants.PROPNAME_ACLINHERITFROM_DOCID,
        getInheritFromFeedType(document), DocumentType.ACL);
  }

  /**
   * Determines the FeedType for the inherited ACL document.
   * The FeedType comes from (in order):
   *   1. SpiConstants.PROPNAME_ACLINHERITFROM_FEEDTYPE
   *   2. SpiConstants.PROPNAME_FEEDTYPE
   *   3. FeedType of this XmlFeed
   *
   * @return the FeedType for the inherited ACL document
   */
  private FeedType getInheritFromFeedType(Document document)
      throws RepositoryException, RepositoryDocumentException {
    String feedType = DocUtils.getOptionalString(document,
        SpiConstants.PROPNAME_ACLINHERITFROM_FEEDTYPE);
    if (feedType == null) {
      feedType = DocUtils.getOptionalString(document,
          SpiConstants.PROPNAME_FEEDTYPE);
    }
    return (feedType == null) ? defaultFeedType
                              : FeedType.findFeedType(feedType);
  }

  /**
   * Constructs the URL for the given doc id, feed type and URL.
   *
   * @throws RepositoryDocumentException if searchUrl is invalid.
   */
  private String getOrConstructUrl(Document document, String urlProperty,
      String docidProperty, FeedType feedType, DocumentType documentType)
      throws RepositoryException, RepositoryDocumentException {
    String recordUrl = DocUtils.getOptionalString(document, urlProperty);
    if (recordUrl != null) {
      if (documentType != DocumentType.ACL) {
        validateUrl(recordUrl, urlProperty);
      }
    } else {
      String docId = DocUtils.getOptionalString(document, docidProperty);
      if (docId != null) {
        // Fabricate a URL from the docid and feedType.
        recordUrl = constructUrl(docId, feedType);
      }
    }
    return recordUrl;
  }

  /**
   * Form either a Google connector URL or a Content URL, based on
   * feed type.
   */
  private String constructUrl(String docid, FeedType feedType) {
    switch (feedType) {
      case CONTENTURL:
        return constructContentUrl(docid);
      case CONTENT:
        return constructGoogleConnectorUrl(docid);
      case WEB:
        return docid;
      default:
        throw new AssertionError(feedType);
    }
  }

  /**
   * Form a Google connector URL.
   *
   * @param docid
   * @return the connector url
   */
  private String constructGoogleConnectorUrl(String docid) {
    StringBuilder buf = new StringBuilder(ServletUtil.PROTOCOL);
    buf.append(dataSource);
    buf.append(".localhost").append(ServletUtil.DOCID);
    buf.append(docid);
    return buf.toString();
  }

  /**
   * Form a Content URL.
   *
   * @param docid
   * @return the contentUrl
   */
  private String constructContentUrl(String docid) {
    Preconditions.checkState(!Strings.isNullOrEmpty(contentUrlPrefix),
                             "contentUrlPrefix must not be null or empty");
    StringBuilder buf = new StringBuilder(contentUrlPrefix);
    ServletUtil.appendQueryParam(buf, ServletUtil.XMLTAG_CONNECTOR_NAME,
                                 dataSource);
    ServletUtil.appendQueryParam(buf, ServletUtil.QUERY_PARAM_DOCID, docid);
    return buf.toString();
  }

  /**
   * Verify that a supplied URL is at least syntactically valid.
   *
   * @param url the URL to validate
   * @param description description of the URL
   * @throws RepositoryDocumentException if the URL is invalid
   */
  private static void validateUrl(String url, String description)
      throws RepositoryDocumentException {
    // Check that this looks like a URL.
    try {
      // The GSA supports SMB URLs, but Java does not.
      if (url != null && url.startsWith("smb:")) {
        new URL(null, url, SmbURLStreamHandler.getInstance());
      } else {
        new URL(url);
      }
    } catch (MalformedURLException e) {
      throw new RepositoryDocumentException(
          "Supplied " + description + " URL " + url + " is malformed.", e);
    }
  }
}
