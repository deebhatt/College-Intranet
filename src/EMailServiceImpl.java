package com.ge.efs.spark.service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.util.Assert;

import com.ge.efs.csm.bean.User;
import com.ge.efs.spark.common.Constants;
import com.ge.efs.spark.common.Lookup;
import com.ge.efs.spark.dao.CommonLookupDao;
import com.ge.efs.spark.domain.CommonLookup;
import com.ge.efs.spark.domain.Deal;
import com.ge.efs.spark.domain.JoinedDeal;
import com.ge.efs.spark.domain.MeetingAgendaBeanExtended;
import com.ge.efs.spark.domain.MeetingBeanExtended;
import com.ge.efs.spark.util.DealFlowUtil;
import com.ge.efs.spark.util.UrlUtil;
import com.ge.efs.spark.util.Util;

@Service("EMailService")
public class EMailServiceImpl extends UniversalServiceImpl implements InitializingBean, EMailService {
  private static final Logger emailLog = Logger.getLogger(EMailServiceImpl.class);

  private String smtpHost;
  private String smtpAuth;
  private String mailFrom;
  private String emailList;
  private String latePitchEmailList;
  private String environment;
  private String equityStakeControlEmailList;
  private String efssparkClosedDealsEmailList;
  private String creditRequestApprovalsEmailList;
  private String emailForSupportFunction;
  private String emailForSupportFunctionsOnAssignment;
  private String pulledDealEmail;
  private CommonLookupDao commonLookupDao;
  private JavaMailSender javaMailSender;

  @Autowired
  GEUserProfileService geUserProfileService;

  @Autowired
  ImprovedNavDealService navDealService;

  @Autowired
  ListService listService;

  /* Changes related to SPAMPS-1051 */
  @Autowired
  private String sparkURL;

  @Autowired
  private VelocityEngine velocityEngine;

  /* Changes related to SPAMPS-1051 end here */

  public void setJavaMailSender(JavaMailSender javaMailSender) {
    this.javaMailSender = javaMailSender;
  }

  public void setVelocityEngine(VelocityEngine velocityEngine) {
    this.velocityEngine = velocityEngine;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.notNull(commonLookupDao, "A CommonLookupDao is required for the EMailService to work.");
  }

  public String getSmtpHost() {
    return smtpHost;
  }

  public String getSmtpAuth() {
    return smtpAuth;
  }

  public String getMailFrom() {
    return mailFrom;
  }

  @Override
  public String getLatePitchEmailList() {
    return latePitchEmailList;
  }

  public void setLatePitchEmailList(String latePitchEmailList) {
    this.latePitchEmailList = latePitchEmailList;
  }

  @Override
  public String getEquityStakeControlEmailList() {
    return equityStakeControlEmailList;
  }

  public void setEquityStakeControlEmailList(String equityStakeControlEmailList) {
    this.equityStakeControlEmailList = equityStakeControlEmailList;
  }

  @Override
  public String getSparkURL() {
    return sparkURL;
  }

  @Override
  public String getEmailList() {
    return emailList;
  }

  public void setEmailList(String emailList) {
    this.emailList = emailList;
  }

  public void setSparkURL(String sparkURL) {
    this.sparkURL = sparkURL;
  }

  public void setSmtpHost(String smtpHost) {
    this.smtpHost = smtpHost;
  }

  public void setSmtpAuth(String smtpAuth) {
    this.smtpAuth = smtpAuth;
  }

  public void setMailFrom(String mailFrom) {
    this.mailFrom = mailFrom;
  }

  @Override
  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public boolean sendMail(String mailToAddress, String mailSubject, String mailBody) {
    return sendMail(mailToAddress, mailSubject, mailBody, false);
  }

  @Override
  public boolean sendMail(List<String> mailToAddresses, String mailSubject, String mailBody) {
    return sendMail(mailToAddresses, mailSubject, mailBody, false);
  }

  @Override
  public boolean sendMail(String mailToAddress, String mailSubject, String mailBody, boolean isPlainText) {
    if (mailToAddress.indexOf(",") > -1) {
      StringTokenizer emailTokens = new StringTokenizer(mailToAddress, ",");
      ArrayList<String> toAddresses = new ArrayList<String>(emailTokens.countTokens());
      for (; emailTokens.hasMoreTokens();) {
        toAddresses.add(emailTokens.nextToken());
      }
      return sendMail(toAddresses, mailSubject, mailBody, isPlainText);
    } else {
      ArrayList<String> toAddresses = new ArrayList<String>(1);
      toAddresses.add(mailToAddress);
      return sendMail(toAddresses, mailSubject, mailBody, isPlainText);
    }
  }

  @Override
  public boolean sendMail(List<String> mailToAddresses, String mailSubject, String mailBody, boolean isPlainText) {
    return sendMail(mailToAddresses, mailSubject, mailBody, isPlainText, false);
  }

  @Override
  public boolean sendMail(List<String> mailToAddresses, String mailSubject, String mailBody, boolean isPlainText, boolean isRestrictedChecked) throws MailException {
    try {
      if (getEnvironment() == null || getEnvironment().length() == 0 || getEnvironment().trim().equals("")) {
        // code for checking whether dealTeam has Restricted List in
        // Production
        List<String> restrictedList = new ArrayList<String>();
        List<CommonLookup> commonLookupList = commonLookupDao.getLookupByGroupName(Lookup.RESTRICTED_EMAIL);
        if (commonLookupList != null && !commonLookupList.isEmpty() && isRestrictedChecked) {
          for (int i = 0; i < commonLookupList.size(); i++) {
            CommonLookup obCommonLookup = commonLookupList.get(i);
            if (obCommonLookup != null && obCommonLookup.getKeyValue2() != null && !obCommonLookup.getKeyValue2().equalsIgnoreCase("")) {
              restrictedList.add(obCommonLookup.getKeyValue2());
            }
          }
        }
        mailToAddresses = DealFlowUtil.dealTeamCheckedRestrictedEmailList(mailToAddresses, restrictedList);
      } else {
        // code for checking whether dealTeam has Safe Email List in DEV
        // and STG
        List<String> safeEmailList = new ArrayList<String>();
        List<CommonLookup> commonLookupList = commonLookupDao.getLookupByGroupName(Lookup.SAFE_EMAIL);
        if (commonLookupList != null && !commonLookupList.isEmpty()) {
          for (int i = 0; i < commonLookupList.size(); i++) {
            CommonLookup obCommonLookup = commonLookupList.get(i);
            if (obCommonLookup != null && obCommonLookup.getKeyValue2() != null && !obCommonLookup.getKeyValue2().equalsIgnoreCase("")) {
              safeEmailList.add(obCommonLookup.getKeyValue2());
            }
          }
        }
        mailToAddresses = DealFlowUtil.dealTeamCheckedSafeEmailList(mailToAddresses, safeEmailList);
      }
      emailLog.debug("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^Inside EmailSeviceImpl method SPRING^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");

      Set<String> mailToAddressesSet = new HashSet<String>(mailToAddresses);
      emailLog.debug("mailToAddress =" + mailToAddressesSet.size());
      for (String str : mailToAddressesSet) {
        emailLog.debug("Email =" + str);
      }

      emailLog.debug("mailSubject = " + mailSubject);

      /**********************************/
      // create a MIME message using the mail sender implementation
      MimeMessage message = javaMailSender.createMimeMessage();

      // create the message using the specified template
      MimeMessageHelper helper;
      try {
        helper = new MimeMessageHelper(message, true, "UTF-8");
      } catch (Exception exception) {
        exception.printStackTrace();
        throw new MailPreparationException("Not able to create a MIME message", exception);
      }

      helper.setFrom(new InternetAddress(getMailFrom()));
      helper.addBcc(emailList);
      InternetAddress[] toAddresses = new InternetAddress[mailToAddressesSet.size()];
      int i = 0;
      for (Iterator<String> mailToAddressesIterate = mailToAddressesSet.iterator(); mailToAddressesIterate.hasNext();) {
        String toAddressesArrayElement = mailToAddressesIterate.next();
        if (toAddressesArrayElement != null && !toAddressesArrayElement.trim().equals("")) {
          toAddresses[i++] = new InternetAddress(toAddressesArrayElement);
          helper.addTo(toAddressesArrayElement);
        }
      }

      helper.setSubject(mailSubject);
      helper.setSentDate(new Date());
      helper.setText(mailBody, !isPlainText);
      javaMailSender.send(message);
    } catch (MessagingException msgEx) {
      emailLog.error(msgEx.getMessage());
      msgEx.printStackTrace(System.out);
      return false;
    } catch (Exception ex) {
      emailLog.error(ex.getMessage());
      ex.printStackTrace(System.out);
      return false;
    }
    return true;
  }

  @Override
  public String setEmailTempSupportFunc(Map model) {
    String text = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/SupportFunction.vm", model);
    emailLog.info("text:" + text);
    return text;
  }

  @Override
  public String sendBudgetWSFailureMail(Long dealId, String budgetOps) {
    String budgetFailureMsg = null;
    StringBuffer emailBody = new StringBuffer();
    String emailSubject = "Budget Approval Failure";
    if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
      emailSubject = emailSubject + " - " + getEnvironment();
    }
    if (budgetOps.equalsIgnoreCase("getBudget")) {
      emailBody.append("<p>The budget approval details for the Deal ID: " + dealId + " cannot be retrieved.</p><br/><br/><p> Error occured while reading response from budget web service.</p>");
      sendMail("", emailSubject, emailBody.toString(), false);
      budgetFailureMsg = "Cannot retrieve approved budget amounts at this time.";
    } else if (budgetOps.equalsIgnoreCase("saveBudget")) {
      emailBody.append("<p>The budget approval details for the Deal ID: " + dealId + " cannot be saved to RAPID.</p><br/><br/><p> Error occured while reading response from budget web service.</p>");
      sendMail("", emailSubject, emailBody.toString(), false);
    }
    return budgetFailureMsg;
  }

  @Override
  public synchronized String sendEmailOnDealClosure(JoinedDeal deal, ArrayList<String> emailToAddressList) {
    String message = "";
    String closedDealEmailListString = getEfssparkClosedDealsEmailList();
    if (closedDealEmailListString != null && !closedDealEmailListString.equals("")) {
      emailToAddressList.add(closedDealEmailListString);
    }
    if (!emailToAddressList.isEmpty()) {
      String emailSubject = deal.getDealName() + " - Deal Closed";
      if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
        emailSubject = emailSubject + " - " + getEnvironment();
      }
      String emailBody = "";
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("deal", deal);
      model.put("dealClosedDate", Constants.emailDate.format(deal.getDealClosedDate()));
      addCommonParameters(model);

      emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/dealClosureEmail.vm", model);

      try {
        log.info("\n...............EMailServiceImpl : sendEmailOnDealClosure:" + emailToAddressList + ":" + emailSubject + "\n" + emailBody.toString());
        sendMail(emailToAddressList, emailSubject, emailBody, false, true);
        message = "Closed deal Email sent successfully to deal team.";
      } catch (Exception e) {
        message = "Error " + e + ", while sending the email.";
      }
    }
    return message;
  }

  @Override
  public synchronized String sendEmailOnDealReopened(JoinedDeal deal) {
    String message = "";
    ArrayList<String> finalEmailList = new ArrayList<String>();
    List<String> list = listService.getReopenDealEmailList();
    for (int i = 0; i < list.size(); i++) {
      finalEmailList.add(list.get(i));
    }
    // finalEmailList.add("sparkhelp@ge.com");
    if (!finalEmailList.isEmpty()) {
      String emailSubject = deal.getDealName() + " - Deal Reopened";
      if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
        emailSubject = emailSubject + " - " + getEnvironment();
      }
      String emailBody = "";
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("deal", deal);
      model.put("dealReopenedDate", Constants.emailDate.format(deal.getReopenedDealDate()));
      addCommonParameters(model);
      emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/dealReopenEmail.vm", model);
      try {
        log.info("\n...............EMailServiceImpl : sendEmailOnDealReopened:" + finalEmailList + ":" + emailSubject + "\n" + emailBody.toString());
        sendMail(finalEmailList, emailSubject, emailBody, false, true);
        message = "Reopened deal Email sent successfully.";
      } catch (Exception e) {
        message = "Error " + e + ", while sending the email.";
      }
    }
    return message;
  }

  @Override
  public synchronized String sendMRTInfoEmailOnDealClosure(JoinedDeal deal, ArrayList<String> emailToAddressList) {
    String message = "";

    try {
      String emailSubject = "Material Risk Takers for the " + deal.getDealName();
      String emailSubject1 = "Material Risk Taker for the " + deal.getDealName();
      if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
        emailSubject = emailSubject + " - " + getEnvironment();
        emailSubject1 = emailSubject1 + " - " + getEnvironment();
      }
      String emailBody = "";
      String emailBody1 = "";
      List<HashMap<String, String>> dealMRTList = listService.getDealMrtList(deal.getDealId());
      for (int i = 0; i < dealMRTList.size(); i++) {
        ArrayList<String> emailList = new ArrayList<String>();
        emailList.add(dealMRTList.get(i).get("EMAIL_ADDRESS"));
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("deal", deal);
        addCommonParameters(model);
        model.put("helpURL", "mailto:sparkhelp@ge.com");
        model.put("username", dealMRTList.get(i).get("NAME"));
        emailBody1 = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/mrtInfoEmail.vm", model);
        sendMail(emailList, emailSubject1, emailBody1, false, true);
      }
      Map<String, Object> model = new HashMap<String, Object>();
      model.put("deal", deal);
      addCommonParameters(model);
      model.put("mrtList", dealMRTList);
      emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/mrtDetailsEmail.vm", model);

      log.info("\n...............EMailServiceImpl : sendMRTInfoEmailOnDealClosure:" + emailToAddressList + ":" + emailSubject + "\n" + emailBody.toString());
      sendMail(emailToAddressList, emailSubject, emailBody, false, true);
      message = "MRT Details Email sent successfully.";
    } catch (Exception e) {
      message = "Error " + e + ", while sending the email.";
    }

    return message;
  }

  @Override
  public String sendCreditRequestApprovalEmail(Deal deal, ArrayList<String> emailToAddressList) {
    String successMsg = "";
    String creditRequestApprovalsEmailListString = getCreditRequestApprovalsEmailList();
    if (creditRequestApprovalsEmailListString != null && !creditRequestApprovalsEmailListString.equals("")) {
      emailToAddressList.add(creditRequestApprovalsEmailListString);
    }
    if (!emailToAddressList.isEmpty()) {
      String emailSubject = deal.getName() + " - Credit Request Approved  ";
      if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
        emailSubject = emailSubject + " - " + getEnvironment();
      }
      log.info("emailSubjecj @@@@@@@@@@@@@@ =" + emailSubject);
      String emailBody = "";

      Map<String, Object> model = new HashMap<String, Object>();
      model.put("dealName", Util.nvl(deal.getName()));
      addCommonParameters(model);

      emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/creditRequestApproval.vm", model);

      try {
        log.info("\n...............EMailServiceImpl : sendCreditRequestApprovalEmail:" + emailToAddressList + ":" + emailSubject + "\n" + emailBody.toString());

        sendMail(emailToAddressList, emailSubject, emailBody.toString(), false, true);
        successMsg = "Closed deal Email sent successfully to deal team.";
      } catch (Exception e) {
        successMsg = "Error " + e + ", while sending the email.";
      }
    }
    return successMsg;
  }

  @Override
  public void sendEquityStakeControlNotification(Deal deal, String userName) {
    if (deal != null) {
      Calendar cal = Calendar.getInstance();
      SimpleDateFormat sdfLock = new SimpleDateFormat("MM/dd/yy HH:mm:ss a");
      TimeZone estTime = TimeZone.getTimeZone("EST");
      sdfLock.setTimeZone(estTime);
      String dealName = deal.getName() != null ? deal.getName() : "";
      String dealId = deal.getId() != null ? deal.getId().toString() : "";
      String companyName = deal.getCompanyName() != null ? deal.getCompanyName() : "";
      String segmentName = deal.getSelectedSegment() != null ? deal.getSelectedSegment() : "";
      String productName = deal.getProductType() != null && deal.getProductType().getName() != null ? deal.getProductType().getName() : "";
      String transactionName = deal.getSelectedTransactionType() != null ? deal.getSelectedTransactionType() : "";
      String description = deal.getDescription() != null ? deal.getDescription() : "";
      String lastUpdatedDate = deal.getLastUpdatedDate() != null ? sdfLock.format(deal.getLastUpdatedDate().getTime()) : "";
      String emailToAddress = getEquityStakeControlEmailList();
      List<String> emailToAddressList = DealFlowUtil.getEmailListFromGroupId(emailToAddress);
      log.info("\n\n\n\n EMAIL DETAILS***********************EquityStakeControlEmailList");
      if (emailToAddressList != null && !emailToAddressList.isEmpty()) {
        String emailSubject = "HR Deal Notification - DEALPRO Deal with 50% Equity Stake or Control";
        if (getEnvironment() != null && getEnvironment().length() > 0) {
          emailSubject = emailSubject + " - " + getEnvironment();
        }

        StringBuffer emailBody = new StringBuffer();
        emailBody.append("<body>");
        emailBody.append("<table widt='100%' style='color: #000;font-weight: normal;font-size: 12px;font-family: Arial, Helvetica, sans-serif; background: #FFF;tex-align: left;'>");
        emailBody.append("<tr><td colspan='4'>EFS HR Team, <br><br></td>");
        emailBody.append("<tr><td colspan='4'>Please be aware the below listed deal has been indicated to have 50% or more Equity Stake or Control. <br><br></td>");
        emailBody.append("<tr><td colspan='4'>Please follow up the deal listed deal team as appropriate.</td>");
        emailBody.append("<tr><td></td>      <td>&nbsp;&nbsp;&nbsp;&nbsp;</td>       <td></td></tr>");
        emailBody.append("<tr><td width ='20%'><b>Deal ID</td><td width ='1%'>:</td><td width ='1%'>&nbsp;</td><td width ='78%'>" + dealId + "</td></tr>");
        emailBody.append("<tr><td><b>Deal Name</td><td>:</td><td>&nbsp;</td><td>" + dealName + "</td></tr>");
        emailBody.append("<tr><td><b>Company Name</td><td>:</td><td>&nbsp;</td><td>" + companyName + "</td></tr>");
        emailBody.append("<tr><td><b>Deal Segment</td><td>:</td><td>&nbsp;</td><td>" + segmentName + "</td></tr>");
        emailBody.append("<tr><td><b>Product Type</td><td>:</td><td>&nbsp;</td><td>" + productName + "</td></tr>");
        emailBody.append("<tr><td><b>Transaction Type</td><td>:</td><td>&nbsp;</td><td>" + transactionName + "</td></tr>");
        emailBody.append("<tr><td><b>Deal Overview</td><td>:</td><td>&nbsp;</td><td>" + description + "</td></tr>");
        emailBody.append("<tr><td><b>Updated/Created Time</td><td>:</td><td>&nbsp;</td><td>" + lastUpdatedDate + "</td></tr>");
        emailBody.append("<tr><td><b>User</td><td>:</td><td>&nbsp;</td><td>" + userName + "</td></tr>");
        emailBody.append("</table>");
        emailBody.append("<br>");
        emailBody.append("<u>THIS IS AN UNMONITORED EMAIL ACCOUNT, PLEASE DO NOT REPLY TO IT.</u>");
        emailBody.append("</body>");

        try {
          log.info("\n...............EDIT DEAL CONTROLLER : sendEquityStakeControlNotification :" + emailToAddress + ":" + emailSubject + "\n\n" + emailBody.toString());
          sendMail(emailToAddressList, emailSubject, emailBody.toString(), false, true);
        } catch (Exception e) {
          log.info(e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }

  public String getCreditRequestApprovalsEmailList() {
    return creditRequestApprovalsEmailList;
  }

  public void setCreditRequestApprovalsEmailList(String creditRequestApprovalsEmailList) {
    this.creditRequestApprovalsEmailList = creditRequestApprovalsEmailList;
  }

  @Override
  public String getEfssparkClosedDealsEmailList() {
    return efssparkClosedDealsEmailList;
  }

  public void setEfssparkClosedDealsEmailList(String efssparkClosedDealsEmailList) {
    this.efssparkClosedDealsEmailList = efssparkClosedDealsEmailList;
  }

  @Override
  public String getEmailForSupportFunction() {
    return emailForSupportFunction;
  }

  public void setEmailForSupportFunction(String emailForSupportFunction) {
    this.emailForSupportFunction = emailForSupportFunction;
  }

  @Override
  public String getEmailForSupportFunctionsOnAssignment() {
    return emailForSupportFunctionsOnAssignment;
  }

  public void setEmailForSupportFunctionsOnAssignment(String emailForSupportFunctionsOnAssignment) {
    this.emailForSupportFunctionsOnAssignment = emailForSupportFunctionsOnAssignment;
  }

  @Override
  public String getPulledDealEmail() {
    return pulledDealEmail;
  }

  public void setPulledDealEmail(String pulledDealEmail) {
    this.pulledDealEmail = pulledDealEmail;
  }

  public CommonLookupDao getCommonLookupDao() {
    return commonLookupDao;
  }

  @Autowired
  public void setCommonLookupDao(CommonLookupDao commonLookupDao) {
    this.commonLookupDao = commonLookupDao;
  }

  @Override
  public boolean sendIDDEmail(String dealId, String fileLocation, User requestor, String folder) {
    boolean isSuccess = false;
    try {

      JoinedDeal deal = navDealService.getJoinedDealByDealId(dealId);

      MimeMessage message = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true);
      helper.setFrom(getMailFrom());

      List<CommonLookup> recipientList = listService.getLookupByGroupName(Lookup.IDD_LIST);

      for (CommonLookup recipient : recipientList) {
        helper.addTo(recipient.getCommonLookupId().getKeyValue1());
      }

      String requestorEmail = requestor.getUserSSOId() + "@mail.ad.ge.com";
      emailLog.info("compliance requestor email: " + requestorEmail);
      helper.addCc(requestorEmail);

      helper.addBcc(emailList);

      helper.setSubject("Compliance Request - " + deal.getDealName());

      StringBuilder body = new StringBuilder();
      body.append("<strong><span style='font-size: 1.2em;'>Compliance Requested</span></strong>");
      body.append("<br>");
      body.append("<br>");
      body.append("Deal Name: <a href='" + UrlUtil.getSparkURL() + "deal/" + deal.getDealId() + "' >" + deal.getDealName() + "</a>");
      body.append("<br>");
      body.append("Deal ID: " + deal.getDealId());
      body.append("<br>");
      body.append("Requested By: " + requestor.getFirstName() + " " + requestor.getLastName());
      body.append("<br>");
      body.append("Requested Time: " + Constants.getEmailDateFormat().format(new Date()));
      body.append("<br>");
      // spamps 1009 - renaming idd to compliance
      body.append("Compliance Library Link: " + folder);
      body.append("<br>");
      body.append("<br>");
      body.append("Please see the attached file for more details.");
      body.append("<br>");
      body.append("<br>");

      Map<String, Object> model = new HashMap<String, Object>();
      model.put("emailBody", body.toString());

      String text = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/iddSubmitEmail.vm", model);
      helper.setText(text, true);

      ClassPathResource image = new ClassPathResource("emailtemplates/dealpro-logo.png");

      if (fileLocation != null) {
        FileSystemResource file = new FileSystemResource(fileLocation);
        helper.addAttachment(file.getFilename(), file);
      }

      helper.addInline("logo", image);
      javaMailSender.send(message);

      isSuccess = true;
    } catch (MailException e) {
      emailLog.error(e);
    } catch (VelocityException e) {
      emailLog.error(e);
    } catch (MessagingException e) {
      emailLog.error(e);
    } catch (Exception e) {
      emailLog.error(e);
    }
    return isSuccess;
  }

  @Override
  public String sendMeetingEmail(String ssoRequester, MeetingBeanExtended thisMeeting) {
    // Get the actual meeting
    String skipLine = "\n";
    // Build the e-mail subject
    StringBuffer mailSubject = new StringBuffer("EFS DealPRO: ");
    mailSubject.append(thisMeeting.getMeetingBean().getMeetingTypeName());
    mailSubject.append(" meeting(s) have been finalized for ");
    mailSubject.append(Constants.getSDateFormat().format(thisMeeting.getStartDateTime()));
    // Build the mail body
    StringBuffer mailBody = new StringBuffer();
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append("Conference Room :           " + thisMeeting.getMeetingLocation() + skipLine);
    mailBody.append("Chairperson :               " + thisMeeting.getChairPersonName() + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append("Call-in Number(s)/Comments:  " + skipLine);
    mailBody.append("                             " + thisMeeting.getMeetingDescription());
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" Segment                             Deal Name                            Time" + skipLine);
    mailBody.append(" ____________________________________________________________________________________________________" + skipLine);
    mailBody.append(" " + skipLine);
    if (thisMeeting.getMeetingBean().getMeetingAgendas() != null && !thisMeeting.getMeetingBean().getMeetingAgendas().isEmpty()) {
      for (MeetingAgendaBeanExtended mA : thisMeeting.getMeetingBean().getMeetingAgendas()) {

        String dSegment = mA.getDeal() != null ? mA.getDeal().getSegment().getParentSegmentName() : "";
        String dName = mA.getDeal() == null ? mA.getPqrOtherDetail() : !mA.getDeal().isRestricted() ? mA.getDealName() : (mA.getDeal().getDisplayName() != null ? mA.getDeal().getDisplayName() : "Restricted Deal") + " | RESTRICTED";
        String aTime = Constants.getTDateFormat().format(mA.getStartDateTime()) + " " + mA.getAgendaDescription();
        String aDocuments = "";
        if (mA.getAgendaDocumentsId() == null || mA.getAgendaDocumentsId().isEmpty()) {
          aDocuments = "\n Paperwork will be distributed at the meeting.\n";
        } else {
          aDocuments = "\n";
        }

        mailBody.append(" " + dSegment);
        if (dSegment.length() > 0) {
          for (int i = dSegment.length() + 1; i < 37; i++) {
            mailBody.append(" ");
          }
        } else {
          mailBody.append("\t\t\t\t\t\t ");
        }
        mailBody.append(dName);
        for (int i = dName.length() + 1; i < 37; i++) {
          mailBody.append(" ");
        }
        mailBody.append(aTime);
        mailBody.append(aDocuments);
      }
    }
    mailBody.append(" " + skipLine);
    mailBody.append("\n\nTo access DealPRO, please navigate to " + getSparkURL() + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append(" " + skipLine);
    mailBody.append("This is an auto-generated e-mail. Please do not reply." + skipLine);

    if (sendMail(ssoRequester + "@mail.ad.ge.com", mailSubject.toString(), mailBody.toString(), true)) {
      StringBuffer sbMail = new StringBuffer("Meeting notification e-mail sent.");
      sendMail(getEmailList(), "-CC-" + mailSubject.toString(), mailBody.toString(), true);
      return sbMail.toString();
    } else {
      return "Failed to send the meeting notification e-mail; Please contact the DEALPRO Team at <a href='mailto:@SPARK Help'>@SPARK Help</a>.";
    }
  }

  /* Changes related to SPAMPS-1051 */
  @Override
  public String sendEmailForCreditFileCompletion(String dealId, String dealName, String username) {
    String message = "";
    List<String> emailListForCreditFileCompletion = commonLookupDao.getEmailListForCreditFileCompletion(dealId);
    if (emailListForCreditFileCompletion != null) {
      String emailSubject = dealName + " - Load Credit File completed";
      if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
        emailSubject = emailSubject + " - " + getEnvironment();
      }
      String emailBody = "";
      Map<String, Object> dataModel = new HashMap<String, Object>();
      dataModel.put("username", username);
      dataModel.put("dealName", dealName);
      dataModel.put("dealId", dealId);
      dataModel.put("sparkURL", Util.nvl(getSparkURL()) + "/deal/" + dealId);
      dataModel.put("imagePath", Util.nvl(getSparkURL()) + "/resources/img/dealpro-logo.png");
      emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/creditFileCompleteEmail.vm", dataModel);

      try {
        emailLog.info("\n...............EmailServiceImpl : sendEmailForCreditFileCompletion:" + emailListForCreditFileCompletion + ":" + emailSubject + "\n" + emailBody.toString());
        sendMail(emailListForCreditFileCompletion, emailSubject, emailBody);
        message = "Credit File Completion sent successfully to deal team.";
      } catch (Exception e) {
        message = "Error " + e + ", while sending the email.";
      }

    }
    return message;
  }

  /* Changes related to SPAMPS-1051 end here */

  @Override
  public boolean sendFourBlockerReport(String to, String name, File report) throws Exception {

    boolean result = false;

    String subject = "Your four blocker report";
    Map<String, Object> model = new HashMap<String, Object>();
    model.put("username", name);
    model.put("imagePath", Util.nvl(getSparkURL()) + "/resources/img/dealpro-logo.png");
    String contents = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/fourBlockerReport.vm", "UTF-8", model);

    MimeMessage message = javaMailSender.createMimeMessage();

    // create the message using the specified template
    MimeMessageHelper helper;

    helper = new MimeMessageHelper(message, true, "UTF-8");

    helper.setFrom(new InternetAddress(getMailFrom()));
      helper.addBcc(emailList);
    helper.addTo(to + "@mail.ad.ge.com");
    helper.addAttachment(report.getName(), report);
    helper.setSubject(subject);
    helper.setSentDate(new Date());
    helper.setText(contents, true);
    javaMailSender.send(message);
    result = true;

    return result;

  }

	@Override
	public boolean sendEmailScorecardCreation(HashMap<String, Object> hm) {
		boolean result = false;
		Map<String, Object> model = new HashMap<String, Object>();
		model.put("imagePath", Util.nvl(getSparkURL()) + "/resources/img/dealpro-logo.png");
		model.putAll(hm);
		model.put("emailType", "scorecardCreation");
		model.put("sparkURL", Util.nvl(getSparkURL()) + "/portfolio/deal/" + hm.get("dealId"));
		String contents = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine,
				"emailtemplates/environmentalScorecardEmail.vm", "UTF-8", model);
		emailLog.info("email content is :"+ contents);
		String emailSubject = "Environmental Scorecard has been automatically created for the deal "
				+ (String) hm.get("DEAL_NAME") + " "+ getEnvironment();
		List<String> toList = new ArrayList<String>();
		if(hm.get("PM_SSO") !=  null){
			String[] pmsso = hm.get("PM_SSO").toString().split(",");
			for(String pm : pmsso){
				toList.add(pm + "@mail.ad.ge.com");
			}
		}
		if(hm.get("SEC_SSO") !=  null){
			String[] smsso = hm.get("SEC_SSO").toString().split(",");
			for(String sm : smsso){
				toList.add(sm + "@mail.ad.ge.com");
			}
		}
		
/*		toList.add(null != hm.get("PM_SSO") ? hm.get("PM_SSO").toString() + "@mail.ad.ge.com" : "");
		toList.add(null != hm.get("SEC_SSO") ? hm.get("SEC_SSO").toString() + "@mail.ad.ge.com" : "");*/
		
		emailLog.info("sending email to : " + toList);
		result = sendMail(toList, emailSubject, contents);
		return result;
	}

	@Override
	public void sendEnvScorecardReminderEmail(HashMap<String, Object> emailParams) {
		
		List<HashMap> deals = (List<HashMap>)emailParams.get("deals");
		List<String> mailedDeals = new ArrayList<String>();
		Iterator<HashMap> iter = deals.iterator();
		while (iter.hasNext()){
			HashMap currentDealData = iter.next();
			List<String> toList = new ArrayList<String>();
			Map<String, Object> model = new HashMap<String, Object>();
			model.put("imagePath", Util.nvl(getSparkURL()) + "/resources/img/dealpro-logo.png");
			model.put("reminderType", emailParams.get("reminderType"));
			model.put("emailType", "reminder");
			model.put("DEAL_ID", currentDealData.get("DEAL_ID"));
			model.put("DEAL_NAME", currentDealData.get("DEAL_NAME"));
			model.put("sparkURL", Util.nvl(getSparkURL()) + "/portfolio/deal/" + currentDealData.get("DEAL_ID"));
			model.put("actionDate", currentDealData.get("ACTION_DATE"));
			String subject = "Environmental Scorecard Reminder for deal: " + currentDealData.get("DEAL_NAME") + " " + getEnvironment();
			String contents = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "emailtemplates/environmentalScorecardEmail.vm", "UTF-8", model);
			if(currentDealData.get("SEC_SSO") != null && "NotStarted".equalsIgnoreCase(emailParams.get("reminderType").toString())){
				for(String secsso : currentDealData.get("SEC_SSO").toString().split(",")){
					toList.add(secsso + "@mail.ad.ge.com");
				}
				/*String toEmail = currentDealData.get("SEC_SSO").toString()+ "@mail.ad.ge.com" ;*/
				boolean result = sendMail(toList, subject, contents);
				if(result){
					mailedDeals.add(currentDealData.get("DEAL_ID").toString());
				}
				emailLog.info("email content is :"+ contents);
			}else if(currentDealData.get("ENV_SVP_SSO") != null && "Pending".equalsIgnoreCase(emailParams.get("reminderType").toString())){
				for(String envsso : currentDealData.get("ENV_SVP_SSO").toString().split(",")){
					toList.add(envsso + "@mail.ad.ge.com");
				}

				/*String toEmail = currentDealData.get("ENV_SVP_SSO").toString()+ "@mail.ad.ge.com" ;*/
				boolean result = sendMail(toList, subject, contents);
				if(result){
					mailedDeals.add(currentDealData.get("DEAL_ID").toString());
				}
				emailLog.info("email content is :"+ contents);
			}
		}
		emailLog.info("Reminder sent for following deals: "+ mailedDeals);
	}
	
	   /**
     * This method should be generic and reusable.
     * 
     * That way we don't need to come here and write a new method for every new email 
     * notification. When we do that we are breaking the "Open/Closed Principle".
     * 
     * 
     * @param to
     *            - A list of valid email addresses (SSOs alone are not allowed)
     * @param templateLocation
     *            - example: "emailtemplates/[fileName].vm"
     * @param templateParams
     *            - all the parameters needed in the VM template file
     *            ('sparkURL' value added automatically)
     * @param subject
     *            - email subject (environment name added automatically)
     * 
     * @return true when email is sent successful, false otherwise
     */
    @Override
    public boolean sendEmailWithTemplate(final List<String> to, final String templateLocation,
            Map<String, Object> templateParams, String emailSubject) {
        boolean sentSuccessfully = false;

        if (to==null || to.isEmpty()) {
            throw new IllegalArgumentException("Email destinataries list cannot be empty.");
        }

        if (getEnvironment() != null && !getEnvironment().trim().equals("") && getEnvironment().length() > 0) {
            emailSubject = emailSubject + " - " + getEnvironment();
        }
        
        // Adding basic parameters
        addCommonParameters(templateParams);
        
        // Converting template into text string
        final String emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, templateLocation, "utf-8", templateParams);
        
        // Send email operation
        try {
            sendMail(to, emailSubject, emailBody, false, true);
            log.info("Email sent successfully: " + emailSubject);
            log.info("Email body: " + emailBody);
            sentSuccessfully = true;
        } catch (Exception e) {
            log.warn("Exception while sending email: " + emailSubject, e);
        }
        return sentSuccessfully;
    }

    
    /**
     * This method returns the email body (html) instead of sending an actual email.
     * 
     * @param templateLocation
     * @param templateParams
     * @return
     */
    @Override
    public String mergeEmailTemplate(final String templateLocation, Map<String, Object> templateParams) throws Exception{
        // Adding basic parameters
        addCommonParameters(templateParams);
        
        // Converting template into text string
        final String emailBody = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, templateLocation, "utf-8", templateParams);
        return emailBody;
    }
  
    /**
     * Common parameters, as of today:
     * - sparkURL : DealPro application URL (this value changes according to the environment)
     * 
     * @param templateParams
     */
    private void addCommonParameters(Map<String, Object> templateParams) {
        templateParams.put("sparkURL", Util.nvl(getSparkURL()));
    }
}