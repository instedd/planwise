describe HomePage do
  it "should logout" do
    goto_page HomePage do |page|
      expect_page GuissoLogin do |page|
        page.form.user_name.set "admin@instedd.org"
        page.form.password.set "admin123"
        page.form.login.click
      end

      expect(page).to have_content 'Projects'

      page.press_signout_button
    end

    expect_page GuissoLogin
  end

  it "should login" do
    goto_page HomePage do
      expect_page GuissoLogin do |page|
        page.form.user_name.set "admin@instedd.org"
        page.form.password.set "admin123"
        page.form.login.click
      end
    end

    expect(page).to have_content 'Projects'
  end
end
