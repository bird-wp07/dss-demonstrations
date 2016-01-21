/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 *
 * This file is part of the "DSS - Digital Signature Services" project.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.validation.executor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.TSLConstant;
import eu.europa.esig.dss.jaxb.detailedreport.DetailedReport;
import eu.europa.esig.dss.jaxb.detailedreport.XmlBasicBuildingBlocks;
import eu.europa.esig.dss.jaxb.detailedreport.XmlConstraint;
import eu.europa.esig.dss.jaxb.detailedreport.XmlConstraintsConclusion;
import eu.europa.esig.dss.jaxb.detailedreport.XmlStatus;
import eu.europa.esig.dss.jaxb.diagnostic.XmlSignatureScopeType;
import eu.europa.esig.dss.jaxb.diagnostic.XmlSignatureScopes;
import eu.europa.esig.dss.jaxb.simplereport.SimpleReport;
import eu.europa.esig.dss.jaxb.simplereport.XmlPolicy;
import eu.europa.esig.dss.jaxb.simplereport.XmlSignature;
import eu.europa.esig.dss.jaxb.simplereport.XmlSignatureScope;
import eu.europa.esig.dss.validation.AttributeValue;
import eu.europa.esig.dss.validation.MessageTag;
import eu.europa.esig.dss.validation.policy.CertificateQualification;
import eu.europa.esig.dss.validation.policy.SignatureQualification;
import eu.europa.esig.dss.validation.policy.SignatureType;
import eu.europa.esig.dss.validation.policy.TLQualification;
import eu.europa.esig.dss.validation.policy.ValidationPolicy;
import eu.europa.esig.dss.validation.policy.rules.Indication;
import eu.europa.esig.dss.validation.policy.rules.SubIndication;
import eu.europa.esig.dss.validation.wrappers.CertificateWrapper;
import eu.europa.esig.dss.validation.wrappers.DiagnosticData;
import eu.europa.esig.dss.validation.wrappers.SignatureWrapper;

/**
 * This class builds a SimpleReport XmlDom from the diagnostic data and detailed validation report.
 */
public class SimpleReportBuilder {

	private final Date currentTime;
	private final ValidationPolicy policy;
	private final DiagnosticData diagnosticData;
	private final DetailedReport detailedReport;

	private int totalSignatureCount = 0;
	private int validSignatureCount = 0;

	public SimpleReportBuilder(Date currentTime, ValidationPolicy policy, DiagnosticData diagnosticData, DetailedReport detailedReport) {
		this.currentTime = currentTime;
		this.policy = policy;
		this.diagnosticData = diagnosticData;
		this.detailedReport = detailedReport;
	}

	/**
	 * This method generates the validation simpleReport.
	 *
	 * @param params
	 *            validation process parameters
	 * @return the object representing {@code SimpleReport}
	 */
	public eu.europa.esig.dss.jaxb.simplereport.SimpleReport build() {

		SimpleReport simpleReport = new SimpleReport();

		addPolicyNode(simpleReport);
		addValidationTime(simpleReport);
		addDocumentName(simpleReport);
		addSignatures(simpleReport);
		addStatistics(simpleReport);

		return simpleReport;
	}

	private void addPolicyNode(SimpleReport report) {
		XmlPolicy xmlpolicy = new XmlPolicy();
		xmlpolicy.setPolicyName(policy.getPolicyName());
		xmlpolicy.setPolicyDescription(policy.getPolicyDescription());
		report.setPolicy(xmlpolicy);
	}

	private void addValidationTime(SimpleReport report) {
		report.setValidationTime(currentTime);
	}

	private void addDocumentName(SimpleReport report) {
		report.setDocumentName(diagnosticData.getDocumentName());
	}

	private void addSignatures(SimpleReport simpleReport) throws DSSException {
		validSignatureCount = 0;
		totalSignatureCount = 0;
		List<SignatureWrapper> signatures = diagnosticData.getSignatures();
		for (SignatureWrapper signature : signatures) {
			addSignature(simpleReport, signature);
		}
	}

	private void addStatistics(SimpleReport simpleReport) {
		simpleReport.setValidSignaturesCount(validSignatureCount);
		simpleReport.setSignaturesCount(totalSignatureCount);
	}

	/**
	 * @param simpleReport
	 * @param signature
	 *            the diagnosticSignature element in the diagnostic data
	 * @throws DSSException
	 */
	private void addSignature(SimpleReport simpleReport, SignatureWrapper signature) throws DSSException {

		totalSignatureCount++;

		String signatureId = signature.getId();
		XmlSignature xmlSignature = new XmlSignature();
		xmlSignature.setId(signatureId);

		addCounterSignature(signature, xmlSignature);
		addSignatureScope(signature, xmlSignature);
		addSigningTime(signature, xmlSignature);
		addSignatureFormat(signature, xmlSignature);
		addSignedBy(signature, xmlSignature);

		XmlConstraintsConclusion basicValidation = getBasicSignatureValidationConclusion(signatureId);
		XmlConstraintsConclusion archivalValidation = getArchivalValidationConclusion(signatureId);

		final Indication archivalIndication = archivalValidation.getConclusion().getIndication();
		final SubIndication archivalSubIndication = archivalValidation.getConclusion().getSubIndication();
		
		List<String> infoList = xmlSignature.getInfos();
		// final List<XmlDom> ltvInfoList = ltvConclusion.getElements("./Info");

		Indication indication = archivalIndication;
		SubIndication subIndication = archivalSubIndication;
		// List<XmlDom> infoList = new ArrayList<XmlDom>();
		// infoList.addAll(ltvInfoList);
		
		for(XmlConstraint constraint : getAllBBBConstraintsForASignature(xmlSignature)) {
			if(constraint.getStatus().equals(XmlStatus.WARNING)) {
				infoList.add(MessageTag.valueOf(constraint.getName().getNameId()+"_ANS").getMessage());
			}
		}

		// final List<XmlDom> basicValidationInfoList = basicValidationConclusion.getElements("./Info");
		// final List<XmlDom> basicValidationWarningList = basicValidationConclusion.getElements("./Warning");
		// final List<XmlDom> basicValidationErrorList = basicValidationConclusion.getElements("./Error");

		final boolean noTimestamp = Indication.INDETERMINATE.equals(archivalIndication) && SubIndication.NO_TIMESTAMP.equals(archivalSubIndication);
		if (noTimestamp) {

			final Indication basicValidationConclusionIndication = basicValidation.getConclusion().getIndication();
			final SubIndication basicValidationConclusionSubIndication = basicValidation.getConclusion().getSubIndication();
			indication = basicValidationConclusionIndication;
			subIndication = basicValidationConclusionSubIndication;
			// infoList = basicValidationInfoList;
			if (!Indication.VALID.equals(basicValidationConclusionIndication)) {

				if (noTimestamp) {
					xmlSignature.getWarnings().add(MessageTag.LABEL_TINTWS.getMessage());
				} else {
					xmlSignature.getWarnings().add(MessageTag.LABEL_TINVTWS.getMessage());
					// for (XmlDom xmlDom : ltvInfoList) {
					// xmlSignature.getInfos().add(xmlDom.getText());
					// }
				}
			}
		}
		xmlSignature.setIndication(indication);
		if (Indication.VALID.equals(indication)) {
			validSignatureCount++;
		}
		if (subIndication != null) {
			xmlSignature.setSubIndication(subIndication);
		}
		// if (basicValidationConclusion != null) {
		// String errorMessage = signature.getErrorMessage();
		// if (StringUtils.isNotEmpty(errorMessage)) {
		// xmlSignature.getInfos().add(StringEscapeUtils.escapeXml(errorMessage));
		// }
		// }
		// if (!Indication.VALID.equals(archivalIndication)) {
		//
		// addBasicInfo(xmlSignature, basicValidationErrorList);
		// }
		// addBasicInfo(xmlSignature, basicValidationWarningList);
		// addBasicInfo(xmlSignature, infoList);

		addSignatureProfile(signature, xmlSignature);

		simpleReport.getSignature().add(xmlSignature);
	}

	private List<XmlConstraint> getAllBBBConstraintsForASignature(XmlSignature signature) {
		List<XmlConstraint> result = new ArrayList<XmlConstraint>();
		for(XmlBasicBuildingBlocks bbb : detailedReport.getBasicBuildingBlocks()) {
			if(bbb.getId().equals(signature.getId())) { // Check if it's the BBB for the signature
				if(bbb.getCV() != null) {
					result.addAll(bbb.getCV().getConstraint());
				} 
				if(bbb.getISC() != null) {
					result.addAll(bbb.getISC().getConstraint());
				}
				if(bbb.getSAV() != null) {
					result.addAll(bbb.getSAV().getConstraint());
				} 
				if(bbb.getVCI() != null) {
					result.addAll(bbb.getVCI().getConstraint());
				} 
				if(bbb.getXCV() != null) {
					result.addAll(bbb.getXCV().getConstraint());
				}
			}
		}
		return result;
	}
	
	private XmlConstraintsConclusion getBasicSignatureValidationConclusion(String signatureId) {
		List<eu.europa.esig.dss.jaxb.detailedreport.XmlSignature> signatures = detailedReport.getSignature();
		for (eu.europa.esig.dss.jaxb.detailedreport.XmlSignature xmlSignature : signatures) {
			if (StringUtils.equals(signatureId, xmlSignature.getId())) {
				return xmlSignature.getValidationProcessBasicSignatures();
			}
		}
		return null;
	}

	private XmlConstraintsConclusion getArchivalValidationConclusion(String signatureId) {
		List<eu.europa.esig.dss.jaxb.detailedreport.XmlSignature> signatures = detailedReport.getSignature();
		for (eu.europa.esig.dss.jaxb.detailedreport.XmlSignature xmlSignature : signatures) {
			if (StringUtils.equals(signatureId, xmlSignature.getId())) {
				return xmlSignature.getValidationProcessArchivalData();
			}
		}
		return null;
	}

	private void addCounterSignature(SignatureWrapper signature, XmlSignature xmlSignature) {
		if (AttributeValue.COUNTERSIGNATURE.equals(signature.getType())) {
			xmlSignature.setType(AttributeValue.COUNTERSIGNATURE);
			xmlSignature.setParentId(signature.getParentId());
		}
	}

	private void addSignatureScope(final SignatureWrapper diagnosticSignature, final XmlSignature xmlSignature) {
		for(XmlSignatureScopeType scopeType : diagnosticSignature.getSignatureScopes().getSignatureScope()) {
			XmlSignatureScope scope = new XmlSignatureScope();
			scope.setName(scopeType.getName());
			scope.setScope(scopeType.getScope());
			scope.setValue(scopeType.getValue());
			xmlSignature.getSignatureScope().add(scope);
		}
	}

	// private void addBasicInfo(final XmlSignature xmlSignature, final List<XmlDom> basicValidationErrorList) {
	// for (final XmlDom error : basicValidationErrorList) {
	// xmlSignature.getErrors().add(error.getText());
	// }
	// }
	
	private void addSigningTime(final SignatureWrapper diagnosticSignature, final XmlSignature xmlSignature) {
		xmlSignature.setSigningTime(diagnosticSignature.getDateTime());
	}

	private void addSignatureFormat(final SignatureWrapper diagnosticSignature, final XmlSignature xmlSignature) {
		xmlSignature.setSignatureFormat(diagnosticSignature.getSignatureFormat());
	}

	private void addSignedBy(final SignatureWrapper diagnosticSignature, final XmlSignature xmlSignature) {
		String unknown = "?";
		String signedBy = unknown;
		String certificateId = diagnosticSignature.getSigningCertificateId();
		if (StringUtils.isNotEmpty(certificateId)) {
			signedBy = diagnosticData.getUsedCertificateById(certificateId).getCommonName();
			if (signedBy.equals(StringUtils.EMPTY)) {
				signedBy = diagnosticData.getUsedCertificateById(certificateId).getGivenName();
				if (signedBy.equals(StringUtils.EMPTY)) {
					signedBy = diagnosticData.getUsedCertificateById(certificateId).getSurname();
					if (signedBy.equals(StringUtils.EMPTY)) {
						signedBy = diagnosticData.getUsedCertificateById(certificateId).getPseudo();
						if (signedBy.equals(StringUtils.EMPTY)) {
							signedBy = unknown;
						}
					}
				}
			}
		}
		xmlSignature.setSignedBy(signedBy);
	}

	/**
	 * Here we determine the type of the signature.
	 */
	private void addSignatureProfile(SignatureWrapper signature, XmlSignature xmlSignature) {
		SignatureType signatureType = SignatureType.NA;
		String certificateId = signature.getSigningCertificateId();
		if (certificateId != null) {
			signatureType = getSignatureType(certificateId);
		}
		xmlSignature.setSignatureLevel(signatureType.name());
	}

	/**
	 * This method returns the type of the qualification of the signature (signing certificate).
	 *
	 * @param signCert
	 * @return
	 */
	private SignatureType getSignatureType(final String certificateId) {

		CertificateWrapper certificate = diagnosticData.getUsedCertificateByIdNullSafe(certificateId);
		final CertificateQualification certQualification = new CertificateQualification();
		certQualification.setQcp(certificate.isCertificateQCP());
		certQualification.setQcpp(certificate.isCertificateQCPPlus());
		certQualification.setQcc(certificate.isCertificateQCC());
		certQualification.setQcsscd(certificate.isCertificateQCSSCD());

		final TLQualification trustedListQualification = new TLQualification();

		final String caqc = certificate.getCertificateTSPServiceType();

		final List<String> qualifiers = certificate.getCertificateTSPServiceQualifiers();

		trustedListQualification.setCaqc(TSLConstant.CA_QC.equals(caqc));
		trustedListQualification.setQcCNoSSCD(isQcNoSSCD(qualifiers));
		trustedListQualification.setQcForLegalPerson(isQcForLegalPerson(qualifiers));
		trustedListQualification.setQcSSCDAsInCert(isQcSscdStatusAsInCert(qualifiers));
		trustedListQualification.setQcWithSSCD(isQcWithSSCD(qualifiers));

		final SignatureType signatureType = SignatureQualification.getSignatureType(certQualification, trustedListQualification);
		return signatureType;
	}

	private boolean isQcNoSSCD(final List<String> qualifiers) {
		return qualifiers.contains(TSLConstant.QC_NO_SSCD) || qualifiers.contains(TSLConstant.QC_NO_SSCD_119612);
	}

	private boolean isQcForLegalPerson(final List<String> qualifiers) {
		return qualifiers.contains(TSLConstant.QC_FOR_LEGAL_PERSON) || qualifiers.contains(TSLConstant.QC_FOR_LEGAL_PERSON_119612);
	}

	private boolean isQcSscdStatusAsInCert(final List<String> qualifiers) {
		return qualifiers.contains(TSLConstant.QCSSCD_STATUS_AS_IN_CERT) || qualifiers.contains(TSLConstant.QCSSCD_STATUS_AS_IN_CERT_119612);
	}

	private boolean isQcWithSSCD(final List<String> qualifiers) {
		return qualifiers.contains(TSLConstant.QC_WITH_SSCD) || qualifiers.contains(TSLConstant.QC_WITH_SSCD_119612);
	}

}
