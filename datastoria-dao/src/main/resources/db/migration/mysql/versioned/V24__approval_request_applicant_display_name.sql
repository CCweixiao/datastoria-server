UPDATE ds_approval_request request
JOIN ds_user_account account
  ON account.tenant_id = request.tenant_id
 AND account.user_id = request.applicant_user_id
SET request.applicant_display_name = account.username
WHERE request.applicant_display_name IS NULL
   OR request.applicant_display_name = ''
   OR request.applicant_display_name = request.applicant_user_id;
