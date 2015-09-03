package org.springframework.content.examples;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractSpringContentTests {

	@Autowired
	private ClaimRepository claimRepo;
	
	@Autowired 
	ClaimFormStore claimFormStore;

	@Before
	public void setUp() {
		
		// delete any existing claim forms
		Iterable<Claim> existingClaims = claimRepo.findAll();
		for (Claim existingClaim : existingClaims) {
			claimFormStore.unsetContent(existingClaim.getClaimForm());
			Assert.assertThat(existingClaim.getClaimForm().getContentId(), is(nullValue()));
			Assert.assertEquals(existingClaim.getClaimForm().getContentLength(), 0);
			Assert.assertThat(claimFormStore.getContent(existingClaim.getClaimForm()), is(nullValue()));
		}
		
		// delete existing claims
		claimRepo.deleteAll();
	}
	
	@Test
	public void test() throws IOException {
		Claim claim = new Claim();
		claim.setFirstName("John");
		claim.setLastName("Smith");
		
		claim.setClaimForm(new ClaimForm());
		claimFormStore.setContent(claim.getClaimForm(), this.getClass().getResourceAsStream("/claim_form.pdf"));
		Assert.assertThat(claim.getClaimForm().getContentId(), is(notNullValue()));
		Assert.assertThat(claim.getClaimForm().getContentId().trim().length(), greaterThan(0));
		Assert.assertEquals(claim.getClaimForm().getContentLength(), 1226609);
		
		claim = claimRepo.save(claim);

		Assert.assertTrue(IOUtils.contentEquals(this.getClass().getResourceAsStream("/claim_form.pdf"), claimFormStore.getContent(claim.getClaimForm())));
	}

	@Test
	public void testUdate() throws IOException {
		Claim claim = new Claim();
		claim.setFirstName("John");
		claim.setLastName("Smith");
		
		claim.setClaimForm(new ClaimForm());
		claimFormStore.setContent(claim.getClaimForm(), this.getClass().getResourceAsStream("/claim_form.pdf"));
		Assert.assertThat(claim.getClaimForm().getContentId(), is(notNullValue()));
		Assert.assertThat(claim.getClaimForm().getContentId().trim().length(), greaterThan(0));
		Assert.assertEquals(claim.getClaimForm().getContentLength(), 1226609);
		
		claim = claimRepo.save(claim);

		claimFormStore.setContent(claim.getClaimForm(), this.getClass().getResourceAsStream("/ACC_IN-1.DOC"));
		claim = claimRepo.save(claim);
		Assert.assertTrue(IOUtils.contentEquals(this.getClass().getResourceAsStream("/ACC_IN-1.DOC"), claimFormStore.getContent(claim.getClaimForm())));
	}
}