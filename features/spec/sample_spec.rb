describe String do
  it "should open google" do
    visit '/'
    click_link 'Gmail'
    screenshot_and_save_page
  end

  it "should go to guisso" do
    visit 'http://guissoweb:4080'
    screenshot_and_save_page
  end

  it "should go to resmap" do
    visit 'http://resmapweb:5080'
    screenshot_and_save_page
  end
end
