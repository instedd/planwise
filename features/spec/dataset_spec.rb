describe "Dataset" do
  before(:each) {
    log_in
  }

  it "should create dataset" do
    goto_page DatasetsPage do |page|
      page.press_primary_button 

      new_window = window_opened_by { 
        page.authorise.click
      }

      within_window new_window do
        click_button 'Approve'
      end

      page.find(".collections li").click
      expand_options
      select_option(1)
      page.import.click
      sleep 3
      expect(page).to have_content 'Ready to use'
    end 
    goto_page HomePage do |page|
      page.press_signout_button
    end  
  end

  it "should delete dataset" do
    create_dataset
    goto_page DatasetsPage do |page|
      page.press_delete_dataset_button
      accept_alert
      expect(page).to_not have_content 'Ready to use'
      expect(page).to have_content 'You have no datasets yet'
    end
  end

  it "should search dataset" do
    create_dataset
    goto_page DatasetsPage do |page|
      page.fill_search_with("Foobar")

      expect(page).to have_content '0 datasets'
      expect(page).to_not have_content 'Kenya Facilities'
      page.fill_search_with("Kenya Facilities")

      expect(page).to have_content '1 dataset'
    end
  end

  it "should cancel dataset import process" do
    goto_page DatasetsPage do |page|
      page.press_primary_button 

      new_window = window_opened_by { 
        page.authorise.click
      }

      within_window new_window do
        click_button 'Approve'
        sleep 3
      end

      page.find(".collections li").click
      expand_options
      select_option(1)
      page.import.click
      page.cancel.click
      
      expect(page).to have_content 'Import cancelled'
    end
  end
end