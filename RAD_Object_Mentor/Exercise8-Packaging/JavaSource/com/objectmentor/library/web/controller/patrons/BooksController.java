package com.objectmentor.library.web.controller.patrons;

import com.objectmentor.library.gateways.*;
import com.objectmentor.library.libraryRules.*;
import com.objectmentor.library.models.*;
import com.objectmentor.library.web.controller.*;

import java.util.*;

public class BooksController extends Controller {

  private LoanReceipt receipt;
  private final String IDENTIFY_PATRON = "patrons/books/identifyPatron.jsp";
  private final String PATRON_SELECTOR = "patrons/books/patronSelector.jsp";
  private final String LOAN_TO_PATRON = "patrons/books/loanToPatron.jsp";
  private final String RETURN_COPY = "patrons/books/returnCopy.jsp";

  public ActionResult returnBook() {
    ActionResult result = new ActionResult();
    getSession().setAttribute("transaction", "return");
    if (isGet()) {
      render(IDENTIFY_PATRON);
    }
    return result;
  }

  public ActionResult loanBook() {
    ActionResult result = new ActionResult();
    getSession().setAttribute("transaction", "loan");
    render(IDENTIFY_PATRON);
    return result;
  }

  public ActionResult matchPatron() {
    ActionResult result = new ActionResult();
    String pattern = getParameter("patronPattern");
    PatronGateway patronGateway = getApplication().getPatronGateway();
    Patron patron = patronGateway.findById(pattern);
    if (patron == null) {
      Set patrons = patronGateway.findLike(pattern);
      if (!patrons.isEmpty()) {
        render(PATRON_SELECTOR);
        setAttribute("patrons", patrons);
      } else {
        render(IDENTIFY_PATRON);
        result.appendToErrorMessage(pattern + " is not a valid patron ID.");
      }
    } else {
      renderTransaction(patron);
    }
    return result;
  }

  private void renderTransaction(Patron patron) {
    setAttribute("date", TimeSource.time());
    setAttribute("loanRecords", getLoanRecords(patron));
    getSession().setAttribute("patron", patron);
    String transaction = (String) getSession().getAttribute("transaction");
    if (transaction.equalsIgnoreCase("loan"))
      render(LOAN_TO_PATRON);
    else if (transaction.equalsIgnoreCase("return"))
      render(RETURN_COPY);
  }

  private List getLoanRecords(Patron patron) {
    List loanRecords = new LinkedList();
    List loanReceipts = getApplication().getBookGateway().findAllLoanReceiptsFor(patron.getId());
    for (int i = 0; i < loanReceipts.size(); i++) {
      LoanReceipt loanReceipt = (LoanReceipt) loanReceipts.get(i);
      LoanRecord record = new LoanRecord();
      MediaCopy copy = loanReceipt.getBorrowedCopy();
      record.id = copy.getId();
      record.title = copy.getMedia().getTitle();
      record.dueDate = loanReceipt.getDueDate();
      record.fine = Library.calculateFine(loanReceipt);
      loanRecords.add(record);
    }
    return loanRecords;
  }

  public ActionResult selectPatron() {
    ActionResult result = new ActionResult();
    String selectedPatron = getParameter("selectedPatron");
    Patron patron = getApplication().getPatronGateway().findById(selectedPatron);
    renderTransaction(patron);
    return result;
  }

  public ActionResult loanCopy() {
    ActionResult result = new ActionResult();
    render(LOAN_TO_PATRON);
    if (isPost()) {
      String copyId = getParameter("copyId");
      MediaCopy copy = getApplication().getBookGateway().findCopyById(copyId);
      Patron patron = (Patron) getSession().getAttribute("patron");
      if (patron == null) {
        render(IDENTIFY_PATRON);
        return ActionResult.makeErrorHandlerResult("No patron matches id \"" + patron.getId() + "\"!");
      } else if (copy == null) {
        renderTransaction(patron);
        return ActionResult.makeErrorHandlerResult("No book matches copy id \"" + copyId + "\"!");
      } else {
        receipt = getLibrary().loan(copy, patron);
        renderTransaction(patron);
        switch (getReceipt().getStatus()) {
          case LoanReceipt.ALREADY_BORROWED:
            return ActionResult.makeErrorHandlerResult("Copy \"" + copyId + "\" is already checked out.");
          case LoanReceipt.DISALLOWED:
            return ActionResult.makeErrorHandlerResult("Copy \"" + copyId + "\" cannot be checked out!");
          case LoanReceipt.OK:
            String title = receipt.getBorrowedCopy().getMedia().getTitle();
            String borrowerName = patron.getFullName();
            result = ActionResult.makeInfoHandlerResult("Copy \"" + title + "\" checked out to borrower \"" + borrowerName + "\".");
            return result;
        }
      }
    }
    return result;
  }

  public ActionResult returnCopy() {
    ActionResult result = new ActionResult();
    Library lib = getApplication().getLibrary();
    MediaGateway mediaGateway = getApplication().getBookGateway();
    if (isPost()) {
      Patron patron = (Patron) getSession().getAttribute("patron");
      String copyId = getParameter("copyId");
      MediaCopy copy = mediaGateway.findCopyById(copyId);
      if (copy == null) {
        result.appendToErrorMessage(copyId + " is not a valid book.");
      } else {
        LoanReceipt loanReciept = copy.getLoanReceipt();
        ReturnReceipt receipt = lib.returnCopy(copy);
        switch (receipt.getStatus()) {
          case ReturnReceipt.UNBORROWED_BOOK:
            result.appendToErrorMessage(copyId + " is not currently loaned to this patron.");
            break;
          case ReturnReceipt.LATE:
            result.appendToWarningMessage("This book is LATE.  The patron owes: " + Library.calculateFine(loanReciept));
            break;
          case ReturnReceipt.OK:
            result.appendToInfoMessage("Returned.");
            break;
        }
      }
      renderTransaction(patron);
    }
    return result;
  }

  public LoanReceipt getReceipt() {
    return receipt;
  }

}
